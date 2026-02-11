package com.braven.karoodashboard.data

/**
 * Represents the current ride session state.
 * All sensor values aggregated from Karoo data streams.
 */
data class SessionState(
    val power: Int = 0,
    val heartRate: Int = 0,
    val cadence: Int = 0,
    val speed: Double = 0.0,        // m/s from SDK
    val elapsedTime: Long = 0L,     // seconds
    val distance: Double = 0.0,     // meters from SDK
    val elevation: Double = 0.0,    // meters
    val grade: Double = 0.0,        // percent
    val temperature: Double = 0.0,  // celsius
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
            append("\"heartRate\":$heartRate,")
            append("\"cadence\":$cadence,")
            append("\"speed\":${formatDouble(speed * 3.6, 1)},") // m/s → km/h
            append("\"elapsedTime\":$elapsedTime,")
            append("\"distance\":${formatDouble(distance / 1000.0, 2)},") // m → km
            append("\"elevation\":${formatDouble(elevation, 1)},")
            append("\"grade\":${formatDouble(grade, 1)},")
            append("\"temperature\":${formatDouble(temperature, 1)},")
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
