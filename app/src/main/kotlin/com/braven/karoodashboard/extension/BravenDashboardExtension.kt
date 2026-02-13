package com.braven.karoodashboard.extension

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import com.braven.karoodashboard.BuildConfig
import com.braven.karoodashboard.MainActivity
import com.braven.karoodashboard.R
import com.braven.karoodashboard.data.DataCollector
import com.braven.karoodashboard.server.IpAddressUtil
import com.braven.karoodashboard.server.NetworkDiscoveryService
import com.braven.karoodashboard.server.WebServer
import com.braven.karoodashboard.trainer.FtmsController
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.MarkLap
import io.hammerhead.karooext.models.RequestBluetooth
import io.hammerhead.karooext.models.ReleaseBluetooth
import io.hammerhead.karooext.models.WriteToRecordMesg
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Main Karoo extension service.
 *
 * Lifecycle:
 * 1. Karoo OS binds to this service via the KAROO_EXTENSION intent filter
 * 2. onCreate: connect to KarooSystem, start data collection, start web server
 * 3. onDestroy: stop everything and disconnect
 *
 * The web server runs on port 8080 and serves dashboards to browsers
 * on the lab Wi-Fi network.
 */
class BravenDashboardExtension : KarooExtension("braven-dashboard", BuildConfig.VERSION_NAME) {

    companion object {
        const val SERVER_PORT = 8080
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "braven_dashboard_channel"

        /** Singleton reference so MainActivity can access the running controller */
        @Volatile
        var instance: BravenDashboardExtension? = null
            private set
    }

    /** Custom data types visible on Karoo ride pages */
    override val types by lazy {
        listOf(
            TargetWattsDataType(extension),
        )
    }

    private lateinit var karooSystem: KarooSystemService
    private lateinit var dataCollector: DataCollector
    private lateinit var webServer: WebServer
    private lateinit var networkDiscovery: NetworkDiscoveryService
    lateinit var ftmsController: FtmsController
        private set

    /** Coroutine scope for trainer state monitoring */
    private val extensionScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** Whether BLE radio has been requested from Karoo OS */
    @Volatile
    private var bleRequested = false

    // ─── FIT Developer Field: Lactate ───────────────────────
    // FIT Base Type 136 = Float32 (IEEE 754)
    private val lactateField = DeveloperField(
        fieldDefinitionNumber = 0,
        fitBaseTypeId = 136,
        fieldName = "Lactate",
        units = "mmol/L",
    )

    /**
     * Reference to the active FIT emitter. Set when ride recording starts,
     * cleared when it ends. Used for direct, synchronous writes — no flow
     * or coroutine indirection that could cause duplicate records.
     */
    @Volatile
    private var fitEmitter: Emitter<FitEffect>? = null

    /** Debounce guard — prevents duplicate writes when emission straddles a 1Hz tick boundary */
    @Volatile
    private var lastFitWriteMs: Long = 0L
    private val fitWriteDebounceMs = 1500L

    /**
     * Called by Karoo OS when ride recording starts.
     * Stores the emitter reference so lactate values can be written
     * directly to FIT records at the exact moment they are submitted.
     */
    override fun startFit(emitter: Emitter<FitEffect>) {
        Timber.i("BravenDashboardExtension: startFit — Lactate developer field registered")
        fitEmitter = emitter
        lastFitWriteMs = 0L

        emitter.setCancellable {
            Timber.i("BravenDashboardExtension: startFit cancelled — ride ended")
            fitEmitter = null
        }
    }

    /**
     * Called from the REST endpoint to submit a lactate reading.
     * Writes directly to the FIT record (if recording) and
     * updates the DataCollector state (broadcast via WebSocket).
     */
    fun submitLactate(value: Double) {
        Timber.i("BravenDashboardExtension: Lactate submitted: $value mmol/L")
        dataCollector.updateLactate(value)

        val now = System.currentTimeMillis()
        fitEmitter?.let { emitter ->
            if (now - lastFitWriteMs > fitWriteDebounceMs) {
                lastFitWriteMs = now
                Timber.i("BravenDashboardExtension: Writing lactate $value mmol/L to FIT record")
                emitter.onNext(
                    WriteToRecordMesg(
                        listOf(FieldValue(lactateField, value))
                    )
                )
            } else {
                Timber.w("BravenDashboardExtension: Skipping duplicate FIT write (debounce)")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("BravenDashboardExtension: Creating extension service")
        instance = this

        // Start as foreground service immediately to prevent Android from killing us
        createNotificationChannel()
        startForegroundService()

        karooSystem = KarooSystemService(applicationContext)
        dataCollector = DataCollector(karooSystem)
        ftmsController = FtmsController(applicationContext)
        webServer = WebServer(
            port = SERVER_PORT,
            assetManager = assets,
            dataProvider = dataCollector,
            onMarkLap = {
                Timber.i("BravenDashboardExtension: Marking lap via hardware action")
                karooSystem.dispatch(MarkLap)
            },
            onLactateUpdate = { value ->
                submitLactate(value)
            },
            // Trainer control callbacks
            onTrainerScan = {
                requestBleAndScan()
            },
            onTrainerConnect = { address ->
                ftmsController.connect(address)
            },
            onTrainerSetPower = { watts ->
                ftmsController.setTargetPower(watts)
            },
            onTrainerDisconnect = {
                ftmsController.disconnect()
                releaseBle()
            },
            onTrainerStatus = {
                ftmsController.statusJson()
            },
        )
        networkDiscovery = NetworkDiscoveryService(
            context = applicationContext,
            httpPort = SERVER_PORT,
            firebaseUrl = BuildConfig.FIREBASE_URL.ifEmpty { null },
        )

        karooSystem.connect { connected ->
            Timber.i("BravenDashboardExtension: KarooSystem connected=$connected")
            if (connected) {
                startServices()
                startTrainerStateMonitor()
            }
        }
    }

    /**
     * Request BLE radio access from Karoo OS, then start scanning.
     * The Karoo uses RequestBluetooth/ReleaseBluetooth to arbitrate
     * the BLE radio between extensions and the OS.
     */
    fun requestBleAndScan() {
        if (!bleRequested) {
            Timber.i("BravenDashboardExtension: Requesting BLE radio access")
            karooSystem.dispatch(RequestBluetooth("braven-ftms"))
            bleRequested = true
        }
        ftmsController.startScan()
    }

    /**
     * Release BLE radio back to Karoo OS.
     */
    fun releaseBle() {
        if (bleRequested) {
            Timber.i("BravenDashboardExtension: Releasing BLE radio")
            karooSystem.dispatch(ReleaseBluetooth("braven-ftms"))
            bleRequested = false
        }
    }

    /**
     * Monitor FtmsController state changes and push them into
     * DataCollector so they're broadcast via WebSocket to dashboards.
     */
    private fun startTrainerStateMonitor() {
        extensionScope.launch {
            ftmsController.status.collect { trainerStatus ->
                dataCollector.updateTrainerState(
                    state = trainerStatus.state.name,
                    deviceName = trainerStatus.deviceName,
                    targetPower = trainerStatus.targetPower,
                    error = trainerStatus.errorMessage,
                )
            }
        }
    }

    override fun onDestroy() {
        Timber.i("BravenDashboardExtension: Destroying extension service")
        instance = null

        try {
            ftmsController.destroy()
            releaseBle()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping FTMS controller")
        }

        try {
            extensionScope.cancel()
        } catch (e: Exception) {
            Timber.w(e, "Error cancelling extension scope")
        }

        try {
            webServer.stopServer()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping web server")
        }

        try {
            networkDiscovery.stop()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping network discovery")
        }

        try {
            dataCollector.stopCollecting()
        } catch (e: Exception) {
            Timber.w(e, "Error stopping data collector")
        }

        karooSystem.disconnect()
        super.onDestroy()
    }

    private fun startServices() {
        // Start collecting ride data from sensors
        dataCollector.startCollecting()

        // Start the embedded web server
        try {
            webServer.start()
            webServer.startBroadcasting()

            // Start network discovery (mDNS + UDP beacon)
            networkDiscovery.start()

            val url = IpAddressUtil.getDashboardUrl(applicationContext, SERVER_PORT)
            Timber.i("BravenDashboardExtension: Dashboard available at $url")
            Timber.i("BravenDashboardExtension: Coach view: $url/coach")
            Timber.i("BravenDashboardExtension: Athlete view: $url/athlete")

            // Update notification with actual URL
            updateNotification(url)
        } catch (e: Exception) {
            Timber.e(e, "BravenDashboardExtension: Failed to start web server")
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Braven Dashboard",
            NotificationManager.IMPORTANCE_LOW // Low importance = no sound, but persistent
        ).apply {
            description = "Braven Lab Dashboard streaming service"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun startForegroundService() {
        val notification = buildNotification("Starting...")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        Timber.i("BravenDashboardExtension: Started as foreground service")
    }

    private fun buildNotification(statusText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Braven Dashboard")
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_braven)
            .setOngoing(true) // Cannot be dismissed
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification(url: String) {
        val notification = buildNotification("Streaming at $url")
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
}
