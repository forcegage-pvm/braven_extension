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
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.extension.KarooExtension
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
    }

    private lateinit var karooSystem: KarooSystemService
    private lateinit var dataCollector: DataCollector
    private lateinit var webServer: WebServer
    private lateinit var networkDiscovery: NetworkDiscoveryService

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("BravenDashboardExtension: Creating extension service")

        // Start as foreground service immediately to prevent Android from killing us
        createNotificationChannel()
        startForegroundService()

        karooSystem = KarooSystemService(applicationContext)
        dataCollector = DataCollector(karooSystem)
        webServer = WebServer(
            port = SERVER_PORT,
            assetManager = assets,
            dataProvider = dataCollector,
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
            }
        }
    }

    override fun onDestroy() {
        Timber.i("BravenDashboardExtension: Destroying extension service")

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
