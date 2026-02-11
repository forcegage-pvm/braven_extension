package com.braven.karoodashboard.server

import android.content.Context
import android.net.wifi.WifiManager
import timber.log.Timber
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Utility to retrieve the Karoo device's Wi-Fi IP address
 * for display on-screen and in logs.
 */
object IpAddressUtil {

    /**
     * Get the device's Wi-Fi IP address as a string.
     * Falls back to iterating network interfaces if WifiManager doesn't work.
     */
    fun getDeviceIpAddress(context: Context): String {
        return try {
            getWifiIpAddress(context) ?: getNetworkInterfaceIpAddress() ?: "Unknown"
        } catch (e: Exception) {
            Timber.w(e, "IpAddressUtil: Failed to get IP address")
            "Unknown"
        }
    }

    /**
     * Build the full dashboard URL for display/QR code.
     */
    fun getDashboardUrl(context: Context, port: Int): String {
        val ip = getDeviceIpAddress(context)
        return "http://$ip:$port"
    }

    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(context: Context): String? {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val ipInt = wifiManager?.connectionInfo?.ipAddress ?: return null
        if (ipInt == 0) return null

        return String.format(
            "%d.%d.%d.%d",
            ipInt and 0xff,
            ipInt shr 8 and 0xff,
            ipInt shr 16 and 0xff,
            ipInt shr 24 and 0xff,
        )
    }

    private fun getNetworkInterfaceIpAddress(): String? {
        return try {
            NetworkInterface.getNetworkInterfaces()?.toList()
                ?.filter { it.isUp && !it.isLoopback }
                ?.flatMap { it.inetAddresses.toList() }
                ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                ?.hostAddress
        } catch (e: Exception) {
            null
        }
    }
}
