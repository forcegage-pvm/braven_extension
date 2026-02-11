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
