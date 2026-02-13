package com.braven.karoodashboard.trainer

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * BLE FTMS (Fitness Machine Service) controller for Wahoo KICKR trainers.
 *
 * Handles:
 * - BLE scanning for FTMS-capable devices
 * - GATT connection management
 * - FTMS control point writes (ERG target power, resistance, simulation)
 * - Connection state broadcasting via StateFlow
 *
 * Architecture:
 *   Dashboard UI → REST API → FtmsController → BLE GATT → KICKR
 *
 * The Karoo pairs the KICKR via ANT+ for sensor data (power/cadence),
 * leaving the BLE radio free for FTMS control commands.
 */
@SuppressLint("MissingPermission")
class FtmsController(private val context: Context) {

    // ─── FTMS UUIDs ────────────────────────────────────────
    companion object {
        /** FTMS Service UUID (0x1826) */
        val FTMS_SERVICE_UUID: UUID = UUID.fromString("00001826-0000-1000-8000-00805F9B34FB")

        /** Fitness Machine Control Point — write + indicate */
        val CONTROL_POINT_UUID: UUID = UUID.fromString("00002AD9-0000-1000-8000-00805F9B34FB")

        /** Fitness Machine Status — notify */
        val STATUS_UUID: UUID = UUID.fromString("00002ADA-0000-1000-8000-00805F9B34FB")

        /** Fitness Machine Feature — read */
        val FEATURE_UUID: UUID = UUID.fromString("00002ACC-0000-1000-8000-00805F9B34FB")

        /** Indoor Bike Data — notify */
        val INDOOR_BIKE_DATA_UUID: UUID = UUID.fromString("00002AD2-0000-1000-8000-00805F9B34FB")

        /** Supported Power Range — read */
        val POWER_RANGE_UUID: UUID = UUID.fromString("00002AD8-0000-1000-8000-00805F9B34FB")

        /** Client Characteristic Configuration Descriptor (for enabling indications/notifications) */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")

        // ─── FTMS Control Point Op Codes ───────────────────
        const val OP_REQUEST_CONTROL: Byte = 0x00
        const val OP_RESET: Byte = 0x01
        const val OP_SET_TARGET_POWER: Byte = 0x05
        const val OP_SET_TARGET_RESISTANCE: Byte = 0x04
        const val OP_SET_INDOOR_BIKE_SIMULATION: Byte = 0x11
        const val OP_START_OR_RESUME: Byte = 0x07
        const val OP_STOP_OR_PAUSE: Byte = 0x08
        const val OP_RESPONSE_CODE: Byte = 0x80.toByte()

        // FTMS Result codes
        const val RESULT_SUCCESS: Byte = 0x01
        const val RESULT_NOT_SUPPORTED: Byte = 0x02
        const val RESULT_INVALID_PARAMETER: Byte = 0x03
        const val RESULT_OPERATION_FAILED: Byte = 0x04
        const val RESULT_CONTROL_NOT_PERMITTED: Byte = 0x05

        private const val SCAN_TIMEOUT_MS = 15_000L
    }

    // ─── Connection State ──────────────────────────────────
    enum class TrainerState {
        DISCONNECTED,
        SCANNING,
        CONNECTING,
        CONNECTED,      // GATT connected, discovering services
        CONTROLLING,    // FTMS control granted — ready for commands
        ERROR
    }

    data class ScannedDevice(
        val name: String,
        val address: String,
        val rssi: Int,
    )

    data class TrainerStatus(
        val state: TrainerState = TrainerState.DISCONNECTED,
        val deviceName: String? = null,
        val targetPower: Int? = null,
        val errorMessage: String? = null,
        val scannedDevices: List<ScannedDevice> = emptyList(),
    )

    private val _status = MutableStateFlow(TrainerStatus())
    val status: StateFlow<TrainerStatus> = _status.asStateFlow()

    // ─── BLE State ─────────────────────────────────────────
    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }
    private val bluetoothAdapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var bleScanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var controlPointChar: BluetoothGattCharacteristic? = null

    /** Queue for serialized BLE GATT writes (Android requires one-at-a-time) */
    private val writeQueue = ConcurrentLinkedQueue<ByteArray>()
    @Volatile
    private var writeInProgress = false

    /** Devices discovered during scan */
    private val scannedDevices = mutableMapOf<String, ScannedDevice>()

    /** Timer for scan timeout */
    private var scanTimeoutRunnable: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())

    // ─── Public API ────────────────────────────────────────

    /**
     * Start scanning for BLE FTMS trainers.
     * Results are accumulated in [status].scannedDevices.
     */
    fun startScan() {
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled) {
            updateStatus(TrainerState.ERROR, errorMessage = "Bluetooth not available or disabled")
            return
        }

        // Stop any existing scan
        stopScan()

        scannedDevices.clear()
        updateStatus(TrainerState.SCANNING)
        Timber.i("FtmsController: Starting BLE scan for FTMS devices")

        bleScanner = adapter.bluetoothLeScanner
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(FTMS_SERVICE_UUID))
            .build()
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device
                val name = device.name ?: "Unknown Trainer"
                val address = device.address

                if (!scannedDevices.containsKey(address)) {
                    Timber.i("FtmsController: Found FTMS device: $name [$address] RSSI=${result.rssi}")
                    scannedDevices[address] = ScannedDevice(name, address, result.rssi)
                    updateStatus(
                        TrainerState.SCANNING,
                        scannedDevices = scannedDevices.values.toList()
                    )
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Timber.e("FtmsController: BLE scan failed with error code $errorCode")
                updateStatus(TrainerState.ERROR, errorMessage = "Scan failed (error $errorCode)")
            }
        }

        bleScanner?.startScan(listOf(scanFilter), scanSettings, scanCallback!!)

        // Auto-stop scan after timeout
        scanTimeoutRunnable = Runnable {
            Timber.i("FtmsController: Scan timeout reached")
            stopScan()
            if (_status.value.state == TrainerState.SCANNING) {
                updateStatus(
                    TrainerState.DISCONNECTED,
                    scannedDevices = scannedDevices.values.toList()
                )
            }
        }
        handler.postDelayed(scanTimeoutRunnable!!, SCAN_TIMEOUT_MS)
    }

    /**
     * Stop active BLE scan.
     */
    fun stopScan() {
        scanTimeoutRunnable?.let { handler.removeCallbacks(it) }
        scanTimeoutRunnable = null
        scanCallback?.let {
            try {
                bleScanner?.stopScan(it)
            } catch (e: Exception) {
                Timber.w(e, "FtmsController: Error stopping scan")
            }
        }
        scanCallback = null
        bleScanner = null
    }

    /**
     * Connect to a specific trainer by BLE address.
     */
    fun connect(address: String) {
        val adapter = bluetoothAdapter ?: run {
            updateStatus(TrainerState.ERROR, errorMessage = "Bluetooth not available")
            return
        }

        stopScan()
        disconnectGatt()

        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: Exception) {
            Timber.e(e, "FtmsController: Invalid BLE address: $address")
            updateStatus(TrainerState.ERROR, errorMessage = "Invalid address: $address")
            return
        }

        val deviceName = device.name ?: "Trainer"
        Timber.i("FtmsController: Connecting to $deviceName [$address]")
        updateStatus(TrainerState.CONNECTING, deviceName = deviceName)

        bluetoothGatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    /**
     * Set ERG mode target power in watts.
     */
    fun setTargetPower(watts: Int) {
        val clampedWatts = watts.coerceIn(0, 2000)
        Timber.i("FtmsController: Setting target power to $clampedWatts W")

        val data = byteArrayOf(
            OP_SET_TARGET_POWER,
            (clampedWatts and 0xFF).toByte(),        // LSB
            ((clampedWatts shr 8) and 0xFF).toByte() // MSB
        )
        enqueueWrite(data)
        updateStatus(targetPower = clampedWatts)
    }

    /**
     * Set resistance level (0.0 to 100.0, in 0.1 increments).
     */
    fun setResistance(level: Double) {
        val clamped = (level * 10).toInt().coerceIn(0, 1000)
        Timber.i("FtmsController: Setting resistance to ${clamped / 10.0}")

        val data = byteArrayOf(
            OP_SET_TARGET_RESISTANCE,
            (clamped and 0xFF).toByte(),
            ((clamped shr 8) and 0xFF).toByte()
        )
        enqueueWrite(data)
    }

    /**
     * Set indoor bike simulation parameters.
     * @param windSpeed m/s (sint16, resolution 0.001)
     * @param grade percent (sint16, resolution 0.01)
     * @param crr coefficient of rolling resistance (uint8, resolution 0.0001)
     * @param cw wind resistance coefficient kg/m (uint8, resolution 0.01)
     */
    fun setSimulation(windSpeed: Double = 0.0, grade: Double = 0.0, crr: Double = 0.004, cw: Double = 0.51) {
        val ws = (windSpeed * 1000).toInt().coerceIn(-32768, 32767)
        val gr = (grade * 100).toInt().coerceIn(-32768, 32767)
        val cr = (crr * 10000).toInt().coerceIn(0, 255)
        val cwVal = (cw * 100).toInt().coerceIn(0, 255)

        Timber.i("FtmsController: Setting simulation: wind=$windSpeed grade=$grade crr=$crr cw=$cw")

        val data = byteArrayOf(
            OP_SET_INDOOR_BIKE_SIMULATION,
            (ws and 0xFF).toByte(),
            ((ws shr 8) and 0xFF).toByte(),
            (gr and 0xFF).toByte(),
            ((gr shr 8) and 0xFF).toByte(),
            cr.toByte(),
            cwVal.toByte()
        )
        enqueueWrite(data)
    }

    /**
     * Disconnect from the trainer and clean up.
     */
    fun disconnect() {
        Timber.i("FtmsController: Disconnecting")
        stopScan()
        disconnectGatt()
        writeQueue.clear()
        writeInProgress = false
        updateStatus(TrainerState.DISCONNECTED, deviceName = null, targetPower = null)
    }

    /**
     * Clean up all resources. Call from extension onDestroy.
     */
    fun destroy() {
        disconnect()
        handler.removeCallbacksAndMessages(null)
    }

    // ─── GATT Callback ────────────────────────────────────

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            val deviceName = gatt.device?.name ?: "Trainer"
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Timber.i("FtmsController: GATT connected to $deviceName — discovering services")
                    updateStatus(TrainerState.CONNECTED, deviceName = deviceName)
                    gatt.discoverServices()
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Timber.i("FtmsController: GATT disconnected from $deviceName (status=$status)")
                    controlPointChar = null
                    bluetoothGatt = null
                    writeQueue.clear()
                    writeInProgress = false

                    val errorMsg = if (status != BluetoothGatt.GATT_SUCCESS) {
                        "Disconnected (GATT error $status)"
                    } else null

                    updateStatus(TrainerState.DISCONNECTED, deviceName = null, targetPower = null, errorMessage = errorMsg)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("FtmsController: Service discovery failed with status $status")
                updateStatus(TrainerState.ERROR, errorMessage = "Service discovery failed")
                gatt.disconnect()
                return
            }

            val ftmsService = gatt.getService(FTMS_SERVICE_UUID)
            if (ftmsService == null) {
                Timber.e("FtmsController: FTMS service not found on device")
                updateStatus(TrainerState.ERROR, errorMessage = "FTMS service not found")
                gatt.disconnect()
                return
            }

            Timber.i("FtmsController: FTMS service found — setting up characteristics")

            // Get control point characteristic
            controlPointChar = ftmsService.getCharacteristic(CONTROL_POINT_UUID)
            if (controlPointChar == null) {
                Timber.e("FtmsController: Control Point characteristic not found")
                updateStatus(TrainerState.ERROR, errorMessage = "Control Point not found")
                gatt.disconnect()
                return
            }

            // Enable indications on the control point (required for FTMS responses)
            enableIndications(gatt, controlPointChar!!)

            // Optionally enable notifications on FTMS Status
            ftmsService.getCharacteristic(STATUS_UUID)?.let { statusChar ->
                // We'll enable this after control point indications are set up
                // (chained through onDescriptorWrite callback)
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            val value = characteristic.value ?: return
            when (characteristic.uuid) {
                CONTROL_POINT_UUID -> handleControlPointResponse(value)
                STATUS_UUID -> handleStatusNotification(value)
                INDOOR_BIKE_DATA_UUID -> handleIndoorBikeData(value)
            }
        }

        // Android 13+ (API 33) overload
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            when (characteristic.uuid) {
                CONTROL_POINT_UUID -> handleControlPointResponse(value)
                STATUS_UUID -> handleStatusNotification(value)
                INDOOR_BIKE_DATA_UUID -> handleIndoorBikeData(value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("FtmsController: Descriptor write failed (status=$status)")
                return
            }

            Timber.i("FtmsController: Descriptor written successfully for ${descriptor.characteristic.uuid}")

            // After indications are enabled, request control of the trainer
            if (descriptor.characteristic.uuid == CONTROL_POINT_UUID) {
                Timber.i("FtmsController: Indications enabled — requesting FTMS control")
                requestControl()
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Timber.e("FtmsController: Characteristic write failed (status=$status)")
            } else {
                Timber.d("FtmsController: Write completed successfully")
            }
            // Process next queued write
            writeInProgress = false
            processWriteQueue()
        }
    }

    // ─── BLE Write Queue ──────────────────────────────────

    private fun enqueueWrite(data: ByteArray) {
        writeQueue.add(data)
        processWriteQueue()
    }

    private fun processWriteQueue() {
        if (writeInProgress) return
        val data = writeQueue.poll() ?: return
        val char = controlPointChar ?: run {
            Timber.w("FtmsController: Control point not available, dropping write")
            return
        }
        val gatt = bluetoothGatt ?: run {
            Timber.w("FtmsController: GATT not connected, dropping write")
            return
        }

        writeInProgress = true
        char.value = data
        char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        val success = gatt.writeCharacteristic(char)
        if (!success) {
            Timber.e("FtmsController: Failed to initiate write to control point")
            writeInProgress = false
        }
    }

    // ─── FTMS Protocol ────────────────────────────────────

    private fun requestControl() {
        Timber.i("FtmsController: Sending REQUEST_CONTROL")
        enqueueWrite(byteArrayOf(OP_REQUEST_CONTROL))
    }

    private fun enableIndications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CCCD_UUID)
        if (descriptor != null) {
            descriptor.value = BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            gatt.writeDescriptor(descriptor)
        } else {
            Timber.w("FtmsController: CCCD descriptor not found on ${characteristic.uuid}")
            // Try requesting control anyway
            requestControl()
        }
    }

    private fun handleControlPointResponse(value: ByteArray) {
        if (value.size < 3) return

        val responseCode = value[0]
        val requestOpCode = value[1]
        val result = value[2]

        Timber.i("FtmsController: Control Point response: op=0x${String.format("%02X", requestOpCode)} result=0x${String.format("%02X", result)}")

        if (responseCode == OP_RESPONSE_CODE) {
            when (result) {
                RESULT_SUCCESS -> {
                    when (requestOpCode) {
                        OP_REQUEST_CONTROL -> {
                            Timber.i("FtmsController: Control granted — trainer ready for commands")
                            updateStatus(TrainerState.CONTROLLING)
                        }
                        OP_SET_TARGET_POWER -> {
                            Timber.i("FtmsController: Target power confirmed")
                        }
                        OP_SET_TARGET_RESISTANCE -> {
                            Timber.i("FtmsController: Target resistance confirmed")
                        }
                        OP_SET_INDOOR_BIKE_SIMULATION -> {
                            Timber.i("FtmsController: Simulation parameters confirmed")
                        }
                    }
                }
                RESULT_NOT_SUPPORTED -> {
                    Timber.w("FtmsController: Operation not supported (op=0x${String.format("%02X", requestOpCode)})")
                }
                RESULT_CONTROL_NOT_PERMITTED -> {
                    Timber.w("FtmsController: Control not permitted — another device may have control")
                    updateStatus(
                        TrainerState.ERROR,
                        errorMessage = "Control not permitted — another device has control"
                    )
                }
                else -> {
                    Timber.w("FtmsController: Unexpected result: 0x${String.format("%02X", result)}")
                }
            }
        }
    }

    private fun handleStatusNotification(value: ByteArray) {
        if (value.isEmpty()) return
        Timber.d("FtmsController: Status notification: ${value.joinToString(" ") { String.format("0x%02X", it) }}")
    }

    private fun handleIndoorBikeData(value: ByteArray) {
        // Indoor Bike Data characteristic — we don't need this for control,
        // as we get power/cadence from ANT+ via Karoo's data streams
        Timber.d("FtmsController: Indoor Bike Data received (${value.size} bytes)")
    }

    // ─── Internal Helpers ─────────────────────────────────

    private fun disconnectGatt() {
        bluetoothGatt?.let { gatt ->
            gatt.disconnect()
            gatt.close()
        }
        bluetoothGatt = null
        controlPointChar = null
    }

    private fun updateStatus(
        state: TrainerState? = null,
        deviceName: String? = SENTINEL_STRING,
        targetPower: Int? = SENTINEL_INT,
        errorMessage: String? = SENTINEL_STRING,
        scannedDevices: List<ScannedDevice>? = null,
    ) {
        val current = _status.value
        _status.value = current.copy(
            state = state ?: current.state,
            deviceName = if (deviceName != SENTINEL_STRING) deviceName else current.deviceName,
            targetPower = if (targetPower != SENTINEL_INT) targetPower else current.targetPower,
            errorMessage = if (errorMessage != SENTINEL_STRING) errorMessage else current.errorMessage,
            scannedDevices = scannedDevices ?: current.scannedDevices,
        )
    }

    /**
     * Convert current trainer status to JSON for REST/WebSocket.
     */
    fun statusJson(): String {
        val s = _status.value
        return buildString {
            append('{')
            append("\"state\":\"${s.state.name}\",")
            append("\"deviceName\":${s.deviceName?.let { "\"$it\"" } ?: "null"},")
            append("\"targetPower\":${s.targetPower ?: "null"},")
            append("\"errorMessage\":${s.errorMessage?.let { "\"$it\"" } ?: "null"},")
            append("\"scannedDevices\":[")
            s.scannedDevices.forEachIndexed { i, d ->
                if (i > 0) append(',')
                append("{\"name\":\"${d.name}\",\"address\":\"${d.address}\",\"rssi\":${d.rssi}}")
            }
            append("]}")
        }
    }
}

// Sentinel values for distinguishing "not provided" from "set to null"
private const val SENTINEL_STRING: String = "\u0000_UNCHANGED_"
private const val SENTINEL_INT: Int = -99999
