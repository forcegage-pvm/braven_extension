package com.braven.karoodashboard.data

/**
 * Represents the current ride session state.
 * All sensor values aggregated from Karoo data streams.
 */
data class SessionState(
    val power: Int = 0,
    val power3sAvg: Int = 0,       // 3-second smoothed average
    val powerZone: Int = 0,         // current power zone (1-7)
    val heartRate: Int = 0,
    val maxHeartRate: Int = 0,      // session max HR
    val cadence: Int = 0,
    val speed: Double = 0.0,        // m/s from SDK
    val averageSpeed: Double = 0.0, // m/s from SDK
    val elapsedTime: Long = 0L,     // seconds
    val distance: Double = 0.0,     // meters from SDK
    val elevation: Double = 0.0,    // meters
    val grade: Double = 0.0,        // percent
    val temperature: Double = 0.0,  // celsius (ambient)
    val coreTemp: Double = 0.0,     // celsius (CORE sensor, 0 = unavailable)
    val batteryPercent: Int = -1,   // -1 = unavailable
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
) {
    /**
     * Serialize to JSON for WebSocket broadcast and REST API.
     * Speed converted to km/h, distance to km for display.
     */
    fun toJson(): String {
        return buildString {
            append('{')
            append("\"power\":$power,")
            append("\"power3sAvg\":$power3sAvg,")
            append("\"powerZone\":$powerZone,")
            append("\"heartRate\":$heartRate,")
            append("\"maxHeartRate\":$maxHeartRate,")
            append("\"cadence\":$cadence,")
            append("\"speed\":${formatDouble(speed * 3.6, 1)},") // m/s → km/h
            append("\"averageSpeed\":${formatDouble(averageSpeed * 3.6, 1)},") // m/s → km/h
            append("\"elapsedTime\":$elapsedTime,")
            append("\"distance\":${formatDouble(distance / 1000.0, 2)},") // m → km
            append("\"elevation\":${formatDouble(elevation, 1)},")
            append("\"grade\":${formatDouble(grade, 1)},")
            append("\"temperature\":${formatDouble(temperature, 1)},")
            append("\"coreTemp\":${formatDouble(coreTemp, 1)},")
            append("\"batteryPercent\":$batteryPercent,")
            append("\"latitude\":$latitude,")
            append("\"longitude\":$longitude,")
            append("\"timestamp\":$timestamp")
            append('}')
        }
    }

    private fun formatDouble(value: Double, decimals: Int): String {
        return String.format("%.${decimals}f", value)
    }
}
