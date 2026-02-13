package com.braven.karoodashboard.data

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.StreamState
import com.braven.karoodashboard.extension.streamDataFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Subscribes to all relevant Karoo data streams and aggregates
 * them into a single [SessionState] flow for the web server to broadcast.
 */
class DataCollector(private val karooSystem: KarooSystemService) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _currentState = MutableStateFlow(SessionState())
    val currentState: StateFlow<SessionState> = _currentState.asStateFlow()

    fun startCollecting() {
        Timber.i("DataCollector: Starting data collection")

        // Power (Watts)
        collectSingleValue(DataType.Type.POWER) { value ->
            _currentState.update { it.copy(power = value.toInt(), timestamp = now()) }
        }

        // Heart Rate (BPM)
        collectSingleValue(DataType.Type.HEART_RATE) { value ->
            _currentState.update { it.copy(heartRate = value.toInt(), timestamp = now()) }
        }

        // Cadence (RPM)
        collectSingleValue(DataType.Type.CADENCE) { value ->
            _currentState.update { it.copy(cadence = value.toInt(), timestamp = now()) }
        }

        // Speed (m/s from SDK)
        collectSingleValue(DataType.Type.SPEED) { value ->
            _currentState.update { it.copy(speed = value, timestamp = now()) }
        }

        // Elapsed Time (seconds)
        collectSingleValue(DataType.Type.ELAPSED_TIME) { value ->
            _currentState.update { it.copy(elapsedTime = (value / 1000.0).toLong(), timestamp = now()) }
        }

        // Distance (meters)
        collectSingleValue(DataType.Type.DISTANCE) { value ->
            _currentState.update { it.copy(distance = value, timestamp = now()) }
        }

        // Elevation (meters) — barometric corrected
        collectSingleValue(DataType.Type.PRESSURE_ELEVATION_CORRECTION) { value ->
            _currentState.update { it.copy(elevation = value, timestamp = now()) }
        }

        // Grade (%)
        collectSingleValue(DataType.Type.ELEVATION_GRADE) { value ->
            _currentState.update { it.copy(grade = value, timestamp = now()) }
        }

        // Temperature (°C)
        collectSingleValue(DataType.Type.TEMPERATURE) { value ->
            _currentState.update { it.copy(temperature = value, timestamp = now()) }
        }

        // 3-second smoothed average power
        collectSingleValue(DataType.Type.SMOOTHED_3S_AVERAGE_POWER) { value ->
            _currentState.update { it.copy(power3sAvg = value.toInt(), timestamp = now()) }
        }

        // Power Zone (1-7)
        collectSingleValue(DataType.Type.POWER_ZONE) { value ->
            _currentState.update { it.copy(powerZone = value.toInt(), timestamp = now()) }
        }

        // Max Heart Rate (session)
        collectSingleValue(DataType.Type.MAX_HR) { value ->
            _currentState.update { it.copy(maxHeartRate = value.toInt(), timestamp = now()) }
        }

        // Average Speed (m/s)
        collectSingleValue(DataType.Type.AVERAGE_SPEED) { value ->
            _currentState.update { it.copy(averageSpeed = value, timestamp = now()) }
        }

        // Core Body Temperature (°C, CORE sensor)
        collectSingleValue(DataType.Type.CORE_TEMP) { value ->
            _currentState.update { it.copy(coreTemp = value, timestamp = now()) }
        }

        // Battery Percent (Karoo device)
        collectSingleValue(DataType.Type.BATTERY_PERCENT) { value ->
            _currentState.update { it.copy(batteryPercent = value.toInt(), timestamp = now()) }
        }

        // ==================== LAP DATA ====================

        // Lap Number (current lap)
        collectSingleValue(DataType.Type.LAP_NUMBER) { value ->
            _currentState.update { it.copy(lapNumber = value.toInt(), timestamp = now()) }
        }

        // Lap Power - Avg power output this lap
        collectSingleValue(DataType.Type.POWER_LAP) { value ->
            _currentState.update { it.copy(lapPower = value.toInt(), timestamp = now()) }
        }

        // Lap Time - Duration of current lap (ms from SDK)
        collectSingleValue(DataType.Type.ELAPSED_TIME_LAP) { value ->
            _currentState.update { it.copy(lapTime = (value / 1000.0).toLong(), timestamp = now()) }
        }

        // Lap Speed - Avg speed this lap (m/s)
        collectSingleValue(DataType.Type.AVERAGE_SPEED_LAP) { value ->
            _currentState.update { it.copy(lapSpeed = value, timestamp = now()) }
        }

        // Lap Heart Rate - Avg HR this lap
        collectSingleValue(DataType.Type.AVERAGE_LAP_HR) { value ->
            _currentState.update { it.copy(lapHeartRate = value.toInt(), timestamp = now()) }
        }

        // Lap Cadence - Avg cadence this lap
        collectSingleValue(DataType.Type.CADENCE_LAP) { value ->
            _currentState.update { it.copy(lapCadence = value.toInt(), timestamp = now()) }
        }

        // Lap Distance - Distance traveled this lap (meters)
        collectSingleValue(DataType.Type.DISTANCE_LAP) { value ->
            _currentState.update { it.copy(lapDistance = value, timestamp = now()) }
        }

        // Lap Normalized Power® - NP this lap
        collectSingleValue(DataType.Type.NORMALIZED_POWER_LAP) { value ->
            _currentState.update { it.copy(lapNormalizedPower = value.toInt(), timestamp = now()) }
        }

        // Lap Max Power - Max power output this lap
        collectSingleValue(DataType.Type.MAX_POWER_LAP) { value ->
            _currentState.update { it.copy(lapMaxPower = value.toInt(), timestamp = now()) }
        }

        // ==================== LAST LAP DATA ====================

        // Last Lap Power - Avg power previous lap
        collectSingleValue(DataType.Type.AVERAGE_POWER_LAST_LAP) { value ->
            _currentState.update { it.copy(lastLapPower = value.toInt(), timestamp = now()) }
        }

        // Last Lap Time - Duration of previous lap (ms from SDK)
        collectSingleValue(DataType.Type.ELAPSED_TIME_LAST_LAP) { value ->
            _currentState.update { it.copy(lastLapTime = (value / 1000.0).toLong(), timestamp = now()) }
        }

        // Last Lap Speed - Avg speed previous lap
        collectSingleValue(DataType.Type.AVERAGE_SPEED_LAST_LAP) { value ->
            _currentState.update { it.copy(lastLapSpeed = value, timestamp = now()) }
        }

        // GPS Location — multi-field data point
        scope.launch {
            karooSystem.streamDataFlow(DataType.Type.LOCATION).collect { state ->
                if (state is StreamState.Streaming) {
                    val values = state.dataPoint.values
                    val lat = values[DataType.Field.LOC_LATITUDE] ?: 0.0
                    val lng = values[DataType.Field.LOC_LONGITUDE] ?: 0.0
                    _currentState.update {
                        it.copy(latitude = lat, longitude = lng, timestamp = now())
                    }
                }
            }
        }

        Timber.i("DataCollector: All streams registered")
    }

    /**
     * Update the lactate value from a manual lab entry.
     * Value persists in the session state until overwritten.
     */
    fun updateLactate(value: Double) {
        _currentState.update {
            it.copy(
                lactate = value,
                lactateTimestamp = System.currentTimeMillis(),
                timestamp = now(),
            )
        }
        Timber.i("DataCollector: Lactate updated to $value mmol/L")
    }

    /**
     * Update trainer control state from FtmsController.
     */
    fun updateTrainerState(
        state: String,
        deviceName: String? = null,
        targetPower: Int? = null,
        error: String? = null,
    ) {
        _currentState.update {
            it.copy(
                trainerState = state,
                trainerDeviceName = deviceName,
                trainerTargetPower = targetPower,
                trainerError = error,
                timestamp = now(),
            )
        }
    }

    fun stopCollecting() {
        Timber.i("DataCollector: Stopping data collection")
        scope.cancel()
    }

    /**
     * Helper to collect a single-value data type stream and invoke [onValue] with each update.
     */
    private fun collectSingleValue(dataTypeId: String, onValue: (Double) -> Unit) {
        scope.launch {
            karooSystem.streamDataFlow(dataTypeId).collect { state ->
                when (state) {
                    is StreamState.Streaming -> {
                        state.dataPoint.singleValue?.let { value ->
                            onValue(value)
                        }
                    }
                    is StreamState.Searching -> {
                        Timber.d("DataCollector: Searching for $dataTypeId")
                    }
                    else -> { /* Idle or NotAvailable */ }
                }
            }
        }
    }

    private fun now(): Long = System.currentTimeMillis()
}
