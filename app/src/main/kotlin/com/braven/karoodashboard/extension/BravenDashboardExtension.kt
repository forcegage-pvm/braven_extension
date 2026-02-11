package com.braven.karoodashboard.extension

import com.braven.karoodashboard.BuildConfig
import com.braven.karoodashboard.data.DataCollector
import com.braven.karoodashboard.server.IpAddressUtil
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
    }

    private lateinit var karooSystem: KarooSystemService
    private lateinit var dataCollector: DataCollector
    private lateinit var webServer: WebServer

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.i("BravenDashboardExtension: Creating extension service")

        karooSystem = KarooSystemService(applicationContext)
        dataCollector = DataCollector(karooSystem)
        webServer = WebServer(
            port = SERVER_PORT,
            assetManager = assets,
            dataProvider = dataCollector,
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

            val url = IpAddressUtil.getDashboardUrl(applicationContext, SERVER_PORT)
            Timber.i("BravenDashboardExtension: Dashboard available at $url")
            Timber.i("BravenDashboardExtension: Coach view: $url/coach")
            Timber.i("BravenDashboardExtension: Athlete view: $url/athlete")
        } catch (e: Exception) {
            Timber.e(e, "BravenDashboardExtension: Failed to start web server")
        }
    }
}
