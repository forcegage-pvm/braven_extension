package com.braven.karoodashboard.server

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import timber.log.Timber
import java.io.OutputStreamWriter
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL

/**
 * Handles network discovery for the Braven Dashboard so lab displays
 * can automatically find the Karoo without knowing its IP address.
 * 
 * Three discovery mechanisms:
 * 1. mDNS/Bonjour - registers service as "braven-karoo._http._tcp.local"
 * 2. UDP Beacon - broadcasts "BRAVEN|<ip>|<port>" every 5 seconds on port 8081
 * 3. Firebase Cloud - uploads IP to Firebase Realtime Database for remote discovery
 */
class NetworkDiscoveryService(
    private val context: Context,
    private val httpPort: Int = 8080,
    private val beaconPort: Int = 8081,
    private val beaconIntervalMs: Long = 5000L,
    private val firebaseUrl: String? = null,  // e.g., "https://your-project.firebaseio.com"
    private val deviceId: String = "braven-karoo"
) {
    companion object {
        const val SERVICE_TYPE = "_http._tcp."
        const val SERVICE_NAME = "braven-karoo"
        const val BEACON_PROTOCOL = "BRAVEN"
        const val FIREBASE_UPDATE_INTERVAL_MS = 30000L  // Update Firebase every 30 seconds
    }

    private var nsdManager: NsdManager? = null
    private var isRegistered = false
    private var beaconJob: Job? = null
    private var beaconSocket: DatagramSocket? = null
    private var firebaseJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            Timber.i("NetworkDiscovery: mDNS registered as ${serviceInfo.serviceName}")
            isRegistered = true
        }

        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Timber.w("NetworkDiscovery: mDNS registration failed, error=$errorCode")
            isRegistered = false
        }

        override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
            Timber.i("NetworkDiscovery: mDNS unregistered")
            isRegistered = false
        }

        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            Timber.w("NetworkDiscovery: mDNS unregistration failed, error=$errorCode")
        }
    }

    /**
     * Start all discovery mechanisms.
     */
    fun start() {
        startMdns()
        startBeacon()
        startFirebaseUpdates()
    }

    /**
     * Stop all discovery mechanisms.
     */
    fun stop() {
        stopFirebaseUpdates()
        stopBeacon()
        stopMdns()
    }

    /**
     * Register mDNS service so clients can find us at "braven-karoo.local"
     */
    private fun startMdns() {
        try {
            nsdManager = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
            if (nsdManager == null) {
                Timber.w("NetworkDiscovery: NsdManager not available")
                return
            }

            val serviceInfo = NsdServiceInfo().apply {
                serviceName = SERVICE_NAME
                serviceType = SERVICE_TYPE
                port = httpPort
            }

            nsdManager?.registerService(
                serviceInfo,
                NsdManager.PROTOCOL_DNS_SD,
                registrationListener
            )

            Timber.i("NetworkDiscovery: mDNS registration requested for $SERVICE_NAME:$httpPort")
        } catch (e: Exception) {
            Timber.e(e, "NetworkDiscovery: Failed to start mDNS")
        }
    }

    private fun stopMdns() {
        try {
            if (isRegistered) {
                nsdManager?.unregisterService(registrationListener)
            }
        } catch (e: Exception) {
            Timber.w(e, "NetworkDiscovery: Error stopping mDNS")
        } finally {
            nsdManager = null
            isRegistered = false
        }
    }

    /**
     * Start UDP beacon that broadcasts our IP address every few seconds.
     * Lab displays can listen for this to auto-discover the Karoo.
     * 
     * Broadcast format: "BRAVEN|192.168.x.x|8080"
     */
    private fun startBeacon() {
        beaconJob = scope.launch {
            try {
                beaconSocket = DatagramSocket().apply {
                    broadcast = true
                    reuseAddress = true
                }

                Timber.i("NetworkDiscovery: UDP beacon started on port $beaconPort")

                while (isActive) {
                    sendBeacon()
                    delay(beaconIntervalMs)
                }
            } catch (e: Exception) {
                Timber.e(e, "NetworkDiscovery: Beacon error")
            }
        }
    }

    private fun sendBeacon() {
        try {
            val ip = IpAddressUtil.getDeviceIpAddress(context)
            if (ip == "Unknown") {
                Timber.d("NetworkDiscovery: No IP address, skipping beacon")
                return
            }

            val message = "$BEACON_PROTOCOL|$ip|$httpPort"
            val data = message.toByteArray(Charsets.UTF_8)

            // Broadcast to 255.255.255.255 (all devices on local network)
            val broadcastAddress = InetAddress.getByName("255.255.255.255")
            val packet = DatagramPacket(data, data.size, broadcastAddress, beaconPort)

            beaconSocket?.send(packet)
            Timber.d("NetworkDiscovery: Beacon sent: $message")
        } catch (e: Exception) {
            Timber.w(e, "NetworkDiscovery: Failed to send beacon")
        }
    }

    private fun stopBeacon() {
        beaconJob?.cancel()
        beaconJob = null

        try {
            beaconSocket?.close()
        } catch (e: Exception) {
            Timber.w(e, "NetworkDiscovery: Error closing beacon socket")
        }
        beaconSocket = null

        Timber.i("NetworkDiscovery: Beacon stopped")
    }

    // ============ Firebase Cloud Discovery ============

    /**
     * Start periodic updates to Firebase Realtime Database.
     * This allows remote discovery without being on the same network.
     */
    private fun startFirebaseUpdates() {
        if (firebaseUrl.isNullOrEmpty()) {
            Timber.i("NetworkDiscovery: Firebase URL not configured, skipping cloud registration")
            return
        }

        firebaseJob = scope.launch {
            Timber.i("NetworkDiscovery: Firebase updates started - $firebaseUrl")

            // Initial upload immediately
            uploadToFirebase()

            // Then update periodically
            while (isActive) {
                delay(FIREBASE_UPDATE_INTERVAL_MS)
                uploadToFirebase()
            }
        }
    }

    private fun stopFirebaseUpdates() {
        firebaseJob?.cancel()
        firebaseJob = null
        Timber.i("NetworkDiscovery: Firebase updates stopped")
    }

    /**
     * Upload current IP address to Firebase Realtime Database.
     * Format: { "ip": "192.168.x.x", "port": 8080, "timestamp": 1234567890, "status": "online" }
     */
    private fun uploadToFirebase() {
        if (firebaseUrl.isNullOrEmpty()) return

        try {
            val ip = IpAddressUtil.getDeviceIpAddress(context)
            if (ip == "Unknown") {
                Timber.d("NetworkDiscovery: No IP address, skipping Firebase upload")
                return
            }

            val json = JSONObject().apply {
                put("ip", ip)
                put("port", httpPort)
                put("timestamp", System.currentTimeMillis())
                put("status", "online")
                put("url", "http://$ip:$httpPort")
            }

            val url = URL("$firebaseUrl/devices/$deviceId.json")
            val connection = url.openConnection() as HttpURLConnection

            try {
                connection.apply {
                    requestMethod = "PUT"
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    connectTimeout = 10000
                    readTimeout = 10000
                }

                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(json.toString())
                    writer.flush()
                }

                val responseCode = connection.responseCode
                if (responseCode in 200..299) {
                    Timber.d("NetworkDiscovery: Firebase updated - $ip:$httpPort")
                } else {
                    Timber.w("NetworkDiscovery: Firebase update failed - HTTP $responseCode")
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Timber.w(e, "NetworkDiscovery: Firebase upload error")
        }
    }
}
