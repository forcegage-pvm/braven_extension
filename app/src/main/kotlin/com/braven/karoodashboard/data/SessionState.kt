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
    // Lap data
    val lapNumber: Int = 1,         // current lap number
    val lapPower: Int = 0,          // avg power this lap (watts)
    val lapTime: Long = 0L,         // elapsed time this lap (seconds)
    val lapSpeed: Double = 0.0,     // avg speed this lap (m/s)
    val lapHeartRate: Int = 0,      // avg HR this lap
    val lapCadence: Int = 0,        // avg cadence this lap
    val lapDistance: Double = 0.0,  // distance this lap (meters)
    val lapNormalizedPower: Int = 0,// NP this lap
    val lapMaxPower: Int = 0,       // max power this lap
    // Last lap data
    val lastLapPower: Int = 0,      // avg power previous lap
    val lastLapTime: Long = 0L,     // duration previous lap (seconds)
    val lastLapSpeed: Double = 0.0, // avg speed previous lap (m/s)
    // Lactate data (manual entry from lab)
    val lactate: Double? = null,         // mmol/L, null = no reading taken yet
    val lactateTimestamp: Long? = null,  // when the lactate was entered (unix ms)
    // Trainer control state (KICKR via BLE FTMS)
    val trainerState: String = "DISCONNECTED",  // DISCONNECTED, SCANNING, CONNECTING, CONNECTED, CONTROLLING, ERROR
    val trainerDeviceName: String? = null,
    val trainerTargetPower: Int? = null,
    val trainerError: String? = null,
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
            // Lap data
            append("\"lapNumber\":$lapNumber,")
            append("\"lapPower\":$lapPower,")
            append("\"lapTime\":$lapTime,")
            append("\"lapSpeed\":${formatDouble(lapSpeed * 3.6, 1)},") // m/s → km/h
            append("\"lapHeartRate\":$lapHeartRate,")
            append("\"lapCadence\":$lapCadence,")
            append("\"lapDistance\":${formatDouble(lapDistance / 1000.0, 2)},") // m → km
            append("\"lapNormalizedPower\":$lapNormalizedPower,")
            append("\"lapMaxPower\":$lapMaxPower,")
            // Last lap data
            append("\"lastLapPower\":$lastLapPower,")
            append("\"lastLapTime\":$lastLapTime,")
            append("\"lastLapSpeed\":${formatDouble(lastLapSpeed * 3.6, 1)},") // m/s → km/h
            // Lactate data
            append("\"lactate\":${lactate ?: "null"},")
            append("\"lactateTimestamp\":${lactateTimestamp ?: "null"},")
            // Trainer control state
            append("\"trainerState\":\"$trainerState\",")
            append("\"trainerDeviceName\":${trainerDeviceName?.let { "\"$it\"" } ?: "null"},")
            append("\"trainerTargetPower\":${trainerTargetPower ?: "null"},")
            append("\"trainerError\":${trainerError?.let { "\"$it\"" } ?: "null"},")
            append("\"timestamp\":$timestamp")
            append('}')
        }
    }

    private fun formatDouble(value: Double, decimals: Int): String {
        return String.format("%.${decimals}f", value)
    }
}
