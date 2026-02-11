# Braven Lab Dashboard — Karoo SDK Extension Technical Specification

---

## Project: Real-Time Karoo-to-External-Display Data Pipeline

**Document Version:** 1.0
**Date:** 10 February 2026
**Author:** Braven Performance Lab — Engineering
**Status:** Technical Specification — Ready for Development

---

## Executive Summary

This document specifies the design, architecture, and implementation plan for a custom Hammerhead Karoo extension that streams real-time ride data from the Karoo to external displays (TVs, tablets, monitors) in the Braven Performance Lab. The extension uses the official Karoo SDK to access live sensor data and hosts an embedded HTTP/WebSocket server on the device, allowing any browser on the lab Wi-Fi network to display a custom coaching dashboard.

**Project Name:** `braven-karoo-dashboard`**Extension Name (user-facing):** Braven Lab Display

---

## Table of Contents

1. Background and Problem Statement
2. Solution Architecture
3. Karoo SDK Technical Reference
4. Extension Design — Karoo Side
5. Dashboard Design — Browser Side
6. Data Schema and API Contract
7. Network and Infrastructure
8. Development Environment Setup
9. Build, Deploy, and Sideload Process
10. Proof of Concept Plan
11. Risk Register
12. Future Enhancements
13. Appendices

---

## 1. Background and Problem Statement

### The Need

During lab-based cycling assessments and coached training sessions, the coaching team needs to view the athlete's real-time ride metrics on large external displays. The Hammerhead Karoo does not natively support screen mirroring, HDMI output, or Miracast.

### Investigated Alternatives

| Method | Feasible | Why Not Chosen |
| --- | --- | --- |
| USB-C video output | ❌ | Karoo USB-C does not support DisplayPort Alt Mode |
| Miracast / wireless display | ⚠️ | Not exposed in Karoo UI; unreliable |
| scrcpy via ADB | ✅ | Works but mirrors small screen; no custom layout; requires tethered PC |
| Sideloaded mirror apps | ⚠️ | Permission issues; unreliable on Karoo OS |
| **Custom SDK extension** | **✅** | **Full data access, custom dashboards, multi-display, no extra hardware** |

### Chosen Solution

Build a custom Karoo extension using the official Karoo SDK that:

- Reads all real-time ride data from the Karoo sensor pipeline
- Hosts an embedded web server on the Karoo
- Serves a custom dashboard web application
- Streams live data via WebSocket to any connected browser

---

## 2. Solution Architecture

### High-Level Architecture

```
┌──────────────────────────────────────────────────────┐
│                   KAROO DEVICE                        │
│                                                       │
│  ┌─────────────────────────────────────────────────┐ │
│  │       braven-karoo-dashboard Extension          │ │
│  │                                                 │ │
│  │  ┌─────────────────┐  ┌──────────────────────┐ │ │
│  │  │  Data Collector  │  │  Embedded Web Server │ │ │
│  │  │                 │  │                      │ │ │
│  │  │  KarooSystem    │  │  HTTP Server (:8080) │ │ │
│  │  │  .addConsumer() │──│  ┌─ GET /            │ │ │
│  │  │                 │  │  │  (Dashboard HTML)  │ │ │
│  │  │  Power ─────────│─▶│  ┌─ GET /coach       │ │ │
│  │  │  Heart Rate ────│─▶│  │  (Coach view)     │ │ │
│  │  │  Cadence ───────│─▶│  ┌─ GET /athlete     │ │ │
│  │  │  Speed ─────────│─▶│  │  (Athlete view)   │ │ │
│  │  │  GPS ───────────│─▶│  ┌─ WS /live         │ │ │
│  │  │  Elevation ─────│─▶│  │  (WebSocket data) │ │ │
│  │  │  Lap data ──────│─▶│  │                    │ │ │
│  │  └─────────────────┘  └──────────┬───────────┘ │ │
│  └──────────────────────────────────│─────────────┘ │
│                                     │ Wi-Fi          │
└─────────────────────────────────────│────────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    ▼                 ▼                 ▼
             Coach iPad        Wall Monitor      Athlete Tablet
             /coach            /athlete           /athlete
          (Safari)          (Chrome kiosk)       (Chrome)
```

### Component Overview

| Component | Technology | Runs On |
| --- | --- | --- |
| Data Collector | Kotlin, Karoo SDK v2 | Karoo device |
| Embedded Server | NanoHTTPD (Java) or Ktor Server (Kotlin) | Karoo device |
| Dashboard Frontend | Vanilla JS + CSS (or lightweight framework) | Browser on any display device |
| Communication Protocol | WebSocket (ws://) | Local Wi-Fi network |
| Data Format | JSON | — |

---

## 3. Karoo SDK Technical Reference

### SDK Source

- **Repository:** `https://github.com/hammerheadnav/karoo-sdk`
- **Distribution:** JitPack — `io.hammerhead:karoo-sdk`
- **Current Version:** v2.x (check repo for latest tag)
- **Language:** Kotlin
- **Minimum Android API:** 26+
- **Target Platform:** Karoo 2 (Android 12), Karoo 3 (Android 12)

### Gradle Dependency

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven { url = uri("<https://jitpack.io>") }
    }
}

// build.gradle.kts (app module)
dependencies {
    implementation("io.hammerhead:karoo-sdk:<latest-version>")
}
```

### Core SDK Classes

**KarooSystem** — Primary API entry point

```kotlin
val karooSystem = KarooSystem(context)
karooSystem.connect { connected ->
    // SDK ready
}
```

**Data Consumption** — Subscribe to live ride data streams

```kotlin
karooSystem.addConsumer(
    dataTypeId = SdkDataType.Type.POWER
) { event ->
    when (event) {
        is OnStreamState.Started -> {
            // Access: event.dataPoint.values
        }
        is OnStreamState.Searching -> { /* sensor searching */ }
        is OnStreamState.NotAvailable -> { /* no sensor */ }
    }
}
```

### Confirmed Available Data Types

Based on the SDK's `SdkDataType.Type` enum:

| Data Type | SDK Constant | Unit |
| --- | --- | --- |
| Power | `POWER` | Watts |
| Heart Rate | `HEART_RATE` | BPM |
| Speed | `SPEED` | m/s |
| Cadence | `CADENCE` | RPM |
| Distance | `DISTANCE` | meters |
| Elapsed Time | `ELAPSED_TIME` | seconds |
| Altitude/Elevation | `ELEVATION` | meters |
| GPS Location | `LOCATION` | lat/lng |
| Grade/Gradient | `GRADE` | % |
| Temperature | `TEMPERATURE` | °C |

> **Note to engineers:** Verify the complete list of available `SdkDataType.Type` values from the SDK source. Additional computed fields (TSS, NP, IF, etc.) may or may not be exposed.
> 

### Extension Manifest Registration

Extensions register via Android manifest with Karoo-specific intent filters:

```xml
<service
    android:name=".BravenDashboardService"
    android:exported="true">
    <intent-filter>
        <action android:name="io.hammerhead.karoo.SDK_EXTENSION" />
    </intent-filter>
    <meta-data
        android:name="io.hammerhead.karoo.SDK_VERSION"
        android:value="2" />
</service>
```

---

## 4. Extension Design — Karoo Side

### Module Structure

```
braven-karoo-dashboard/
├── app/
│   ├── src/main/
│   │   ├── java/com/braven/karoodashboard/
│   │   │   ├── BravenDashboardService.kt      // Main extension service
│   │   │   ├── DataCollector.kt                // SDK data stream manager
│   │   │   ├── WebServer.kt                    // HTTP + WebSocket server
│   │   │   ├── SessionState.kt                 // Current ride state model
│   │   │   └── JsonSerializer.kt               // Data → JSON conversion
│   │   ├── assets/
│   │   │   ├── index.html                      // Default dashboard
│   │   │   ├── coach.html                      // Coach view
│   │   │   ├── athlete.html                    // Athlete view
│   │   │   ├── css/
│   │   │   │   └── dashboard.css
│   │   │   └── js/
│   │   │       ├── websocket-client.js
│   │   │       └── dashboard-renderer.js
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── build.gradle.kts
└── settings.gradle.kts
```

### BravenDashboardService.kt — Core Service

```kotlin
class BravenDashboardService : Service() {

    private lateinit var karooSystem: KarooSystem
    private lateinit var dataCollector: DataCollector
    private lateinit var webServer: WebServer

    override fun onCreate() {
        super.onCreate()

        karooSystem = KarooSystem(this)
        dataCollector = DataCollector(karooSystem)
        webServer = WebServer(
            port = 8080,
            assetManager = assets,
            dataProvider = dataCollector
        )

        karooSystem.connect { connected ->
            if (connected) {
                dataCollector.startCollecting()
                webServer.start()
                logServerAddress()
            }
        }
    }

    override fun onDestroy() {
        webServer.stop()
        dataCollector.stopCollecting()
        karooSystem.disconnect()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun logServerAddress() {
        val wifiManager = getSystemService(WIFI_SERVICE) as WifiManager
        val ip = Formatter.formatIpAddress(wifiManager.connectionInfo.ipAddress)
        Log.i("BravenDashboard", "Dashboard available at <http://$ip:8080>")
    }
}
```

### DataCollector.kt — Ride Data Aggregator

```kotlin
class DataCollector(private val karooSystem: KarooSystem) {

    private val _currentState = MutableStateFlow(SessionState())
    val currentState: StateFlow<SessionState> = _currentState.asStateFlow()

    private val consumers = mutableListOf<String>()

    fun startCollecting() {
        registerConsumer(SdkDataType.Type.POWER) { values ->
            _currentState.update { it.copy(power = values.firstOrNull()?.toInt() ?: 0) }
        }
        registerConsumer(SdkDataType.Type.HEART_RATE) { values ->
            _currentState.update { it.copy(heartRate = values.firstOrNull()?.toInt() ?: 0) }
        }
        registerConsumer(SdkDataType.Type.CADENCE) { values ->
            _currentState.update { it.copy(cadence = values.firstOrNull()?.toInt() ?: 0) }
        }
        registerConsumer(SdkDataType.Type.SPEED) { values ->
            _currentState.update { it.copy(speed = values.firstOrNull() ?: 0.0) }
        }
        registerConsumer(SdkDataType.Type.ELAPSED_TIME) { values ->
            _currentState.update { it.copy(elapsedTime = values.firstOrNull()?.toLong() ?: 0L) }
        }
        registerConsumer(SdkDataType.Type.DISTANCE) { values ->
            _currentState.update { it.copy(distance = values.firstOrNull() ?: 0.0) }
        }
    }

    private fun registerConsumer(
        type: SdkDataType.Type,
        onData: (List<Double>) -> Unit
    ) {
        karooSystem.addConsumer(type) { event ->
            when (event) {
                is OnStreamState.Started -> {
                    onData(event.dataPoint.values)
                }
                else -> { /* handle searching/unavailable */ }
            }
        }
    }

    fun stopCollecting() {
        // Remove consumers if SDK supports it
    }
}
```

### SessionState.kt — Data Model

```kotlin
data class SessionState(
    val power: Int = 0,
    val heartRate: Int = 0,
    val cadence: Int = 0,
    val speed: Double = 0.0,
    val elapsedTime: Long = 0L,
    val distance: Double = 0.0,
    val elevation: Double = 0.0,
    val grade: Double = 0.0,
    val temperature: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toJson(): String {
        return """
        {
            "power": $power,
            "heartRate": $heartRate,
            "cadence": $cadence,
            "speed": ${String.format("%.1f", speed * 3.6)},
            "elapsedTime": $elapsedTime,
            "distance": ${String.format("%.2f", distance / 1000.0)},
            "elevation": ${String.format("%.1f", elevation)},
            "grade": ${String.format("%.1f", grade)},
            "temperature": ${String.format("%.1f", temperature)},
            "latitude": $latitude,
            "longitude": $longitude,
            "timestamp": $timestamp
        }
        """.trimIndent()
    }
}
```

### WebServer.kt — Embedded HTTP + WebSocket Server

**Recommended library: NanoHTTPD** (minimal, single-file Java HTTP server — battle-tested on Android)

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
}
```

```kotlin
class WebServer(
    private val port: Int,
    private val assetManager: AssetManager,
    private val dataProvider: DataCollector
) : NanoWSD(port) {

    private val connectedClients = CopyOnWriteArrayList<BravenWebSocket>()
    private var broadcastJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun openWebSocket(handshake: IHTTPSession): WebSocket {
        return BravenWebSocket(handshake)
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri

        // Route to appropriate dashboard
        val assetPath = when {
            uri == "/" || uri == "/index.html" -> "index.html"
            uri == "/coach" || uri == "/coach.html" -> "coach.html"
            uri == "/athlete" || uri == "/athlete.html" -> "athlete.html"
            uri.startsWith("/css/") -> uri.removePrefix("/")
            uri.startsWith("/js/") -> uri.removePrefix("/")
            uri == "/live" -> return super.serve(session) // WebSocket upgrade
            uri == "/api/status" -> return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                dataProvider.currentState.value.toJson()
            )
            else -> return newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT,
                "Not Found"
            )
        }

        return try {
            val inputStream = assetManager.open(assetPath)
            val mimeType = getMimeType(assetPath)
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: Exception) {
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT,
                "Asset not found: $assetPath"
            )
        }
    }

    fun startBroadcasting() {
        broadcastJob = scope.launch {
            dataProvider.currentState.collect { state ->
                val json = state.toJson()
                connectedClients.forEach { client ->
                    try {
                        client.send(json)
                    } catch (e: Exception) {
                        connectedClients.remove(client)
                    }
                }
            }
        }
    }

    private fun getMimeType(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".svg") -> "image/svg+xml"
        else -> "application/octet-stream"
    }

    inner class BravenWebSocket(handshake: IHTTPSession) : WebSocket(handshake) {
        override fun onOpen() {
            connectedClients.add(this)
        }
        override fun onClose(code: WebSocketFrame.CloseCode?, reason: String?, initiatedByRemote: Boolean) {
            connectedClients.remove(this)
        }
        override fun onMessage(message: WebSocketFrame?) { /* client messages if needed */ }
        override fun onPong(pong: WebSocketFrame?) {}
        override fun onException(exception: IOException?) {
            connectedClients.remove(this)
        }
    }
}
```

---

## 5. Dashboard Design — Browser Side

### URL Routing

| URL | View | Target Audience |
| --- | --- | --- |
| `http://<karoo-ip>:8080/` | Default dashboard | General |
| `http://<karoo-ip>:8080/coach` | Coach dashboard — all metrics, detailed analytics | Coach |
| `http://<karoo-ip>:8080/athlete` | Athlete dashboard — big numbers, zone colours | Athlete on secondary screen |
| `http://<karoo-ip>:8080/api/status` | JSON snapshot (HTTP GET) | Debugging / integration |
| `ws://<karoo-ip>:8080/live` | WebSocket live stream | Dashboard JS clients |

### Coach Dashboard Layout

```
┌─────────────────────────────────────────────────────────────┐
│  BRAVEN PERFORMANCE LAB          Athlete: [Name]   [LIVE]  │
├──────────────┬──────────────┬──────────────┬────────────────┤
│              │              │              │                │
│   POWER      │   HEART RATE │   CADENCE    │    SPEED       │
│   285w       │   162 bpm    │   92 rpm     │    38.4 km/h   │
│   [Zone: Z4] │   [Zone: Z4] │              │                │
│              │              │              │                │
├──────────────┴──────────────┴──────────────┴────────────────┤
│                                                             │
│   [POWER GRAPH — Last 5 minutes — rolling line chart]       │
│                                                             │
├──────────────┬──────────────┬──────────────┬────────────────┤
│  Elapsed     │  Distance    │  Elevation   │  Grade         │
│  20:45       │  12.34 km    │  245 m       │  2.3%          │
├──────────────┴──────────────┴──────────────┴────────────────┤
│  Lap: 3  |  Lap Time: 04:12  |  Avg Power (lap): 278w      │
└─────────────────────────────────────────────────────────────┘
```

### Athlete Dashboard Layout

```
┌─────────────────────────────────────────┐
│                                         │
│              285w                       │
│           [POWER — massive font]        │
│           ██████████░░ Zone 4           │
│                                         │
│      162 bpm          92 rpm            │
│      [HR]             [CADENCE]         │
│                                         │
│              20:45                      │
│           [ELAPSED TIME]                │
│                                         │
└─────────────────────────────────────────┘
```

### WebSocket Client (JavaScript)

```jsx
// websocket-client.js

class BravenWebSocketClient {
    constructor(onData, onStatus) {
        this.onData = onData;
        this.onStatus = onStatus;
        this.ws = null;
        this.reconnectInterval = 2000;
        this.connect();
    }

    connect() {
        const host = window.location.host;
        this.ws = new WebSocket(`ws://${host}/live`);

        this.ws.onopen = () => {
            console.log('Connected to Karoo');
            this.onStatus('connected');
        };

        this.ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this.onData(data);
            } catch (e) {
                console.error('Parse error:', e);
            }
        };

        this.ws.onclose = () => {
            this.onStatus('disconnected');
            setTimeout(() => this.connect(), this.reconnectInterval);
        };

        this.ws.onerror = (err) => {
            console.error('WebSocket error:', err);
            this.ws.close();
        };
    }
}

// Usage
const client = new BravenWebSocketClient(
    (data) => {
        document.getElementById('power').textContent = data.power + 'w';
        document.getElementById('hr').textContent = data.heartRate + ' bpm';
        document.getElementById('cadence').textContent = data.cadence + ' rpm';
        document.getElementById('speed').textContent = data.speed + ' km/h';
        document.getElementById('time').textContent = formatTime(data.elapsedTime);
        document.getElementById('distance').textContent = data.distance + ' km';
        updateZoneColours(data);
    },
    (status) => {
        document.getElementById('status').textContent = status;
        document.getElementById('status').className = status;
    }
);

function formatTime(seconds) {
    const m = Math.floor(seconds / 60).toString().padStart(2, '0');
    const s = (seconds % 60).toString().padStart(2, '0');
    return `${m}:${s}`;
}

function updateZoneColours(data) {
    // Apply zone-based background colours
    const powerZone = getPowerZone(data.power);
    document.getElementById('power-container').className = `zone-${powerZone}`;

    const hrZone = getHRZone(data.heartRate);
    document.getElementById('hr-container').className = `zone-${hrZone}`;
}
```

---

## 6. Data Schema and API Contract

### WebSocket Message Format

**Direction:** Server (Karoo) → Client (Browser)
**Frequency:** Every state change (typically 1Hz from sensors, can be throttled)
**Format:** JSON

```json
{
    "power": 285,
    "heartRate": 162,
    "cadence": 92,
    "speed": 38.4,
    "elapsedTime": 1245,
    "distance": 12.34,
    "elevation": 245.0,
    "grade": 2.3,
    "temperature": 22.5,
    "latitude": -25.7479,
    "longitude": 28.2293,
    "timestamp": 1707548400000
}
```

### Field Specifications

| Field | Type | Unit | Source | Update Rate |
| --- | --- | --- | --- | --- |
| `power` | Integer | Watts | Power meter via ANT+/BLE | ~1 Hz |
| `heartRate` | Integer | BPM | HR strap via ANT+/BLE | ~1 Hz |
| `cadence` | Integer | RPM | Cadence sensor or power meter | ~1 Hz |
| `speed` | Float | km/h | Speed sensor or GPS | ~1 Hz |
| `elapsedTime` | Long | Seconds | Karoo timer | ~1 Hz |
| `distance` | Float | Kilometers | Computed | ~1 Hz |
| `elevation` | Float | Meters | Barometric altimeter | ~1 Hz |
| `grade` | Float | Percent | Computed from elevation | ~1 Hz |
| `temperature` | Float | °C | Onboard sensor | ~0.1 Hz |
| `latitude` | Double | Degrees | GPS | ~1 Hz |
| `longitude` | Double | Degrees | GPS | ~1 Hz |
| `timestamp` | Long | Unix ms | System clock | Every message |

### HTTP REST Endpoint

**GET** `/api/status`

Returns the current state as a single JSON object (same schema as WebSocket messages). Useful for debugging and for systems that can't use WebSocket.

---

## 7. Network and Infrastructure

### Lab Wi-Fi Requirements

| Requirement | Specification |
| --- | --- |
| Network type | Standard 2.4GHz or 5GHz Wi-Fi |
| Karoo and display devices | Must be on same subnet |
| SSID isolation | Must be OFF (devices must be able to reach each other) |
| Bandwidth | Minimal — ~1 KB/s per connected client |
| Latency | <50ms local network typical |

### Karoo IP Address Management

**Recommended: DHCP Reservation**

- Assign a static IP to the Karoo's MAC address on your lab router
- Example: `192.168.1.100`
- Bookmark `http://192.168.1.100:8080` on all display devices

**Alternative: mDNS (Zeroconf)**

- Register the service as `braven-karoo.local` using Android NsdManager
- Browsers navigate to `http://braven-karoo.local:8080`
- May not work on all Smart TVs

**Alternative: QR Code on Karoo Screen**

- Extension displays a QR code with the dashboard URL on the Karoo screen
- Scan with tablet/phone to open dashboard

### Firewall Considerations

The lab router must not block:

- TCP port 8080 (or your chosen port) between local devices
- WebSocket upgrade requests (HTTP → WS)

---

## 8. Development Environment Setup

### Prerequisites

| Tool | Version | Purpose |
| --- | --- | --- |
| Android Studio | Latest stable (Hedgehog+) | IDE |
| Kotlin | 1.9+ | Language |
| JDK | 17+ | Build toolchain |
| ADB | Latest | Deploy to Karoo |
| Karoo device | Karoo 2 or Karoo 3 | Target device |
| USB-C cable | Data-capable | ADB connection |

### Step-by-Step Setup

**1. Create new Android project**

```
- Template: No Activity (or Empty Activity)
- Package: com.braven.karoodashboard
- Min SDK: API 26
- Language: Kotlin
- Build: Kotlin DSL (Gradle)
```

**2. Add SDK dependency**

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("<https://jitpack.io>") }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("io.hammerhead:karoo-sdk:<latest>")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("org.nanohttpd:nanohttpd-websocket:2.3.1")
}
```

**3. Add required permissions to AndroidManifest.xml**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

**4. Enable Developer Mode on Karoo**

- Go to Settings → About
- Tap "Build Number" 7 times
- Enable USB Debugging in Developer Options

**5. Verify ADB Connection**

```bash
adb devices
# Should list Karoo device
```

---

## 9. Build, Deploy, and Sideload Process

### Build APK

```bash
# From project root
./gradlew assembleDebug

# APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

### Deploy via ADB

```bash
# Install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# Verify installation
adb shell pm list packages | grep braven

# View logs
adb logcat | grep BravenDashboard
```

### Wireless ADB (for cable-free iteration)

```bash
# Initial setup (one-time, via USB)
adb tcpip 5555
adb connect 192.168.1.100:5555

# Now disconnect USB cable
# All adb commands work wirelessly
adb install -r app-debug.apk
```

### Update Cycle

```
Code change → Build (30s) → adb install (5s) → Test on Karoo
```

Android Studio can also be configured to deploy directly to the Karoo as a run target.

---

## 10. Proof of Concept Plan

### Phase 1: Validate Network Server (Day 1-2)

**Objective:** Confirm that a sideloaded extension can open a server socket and serve HTTP to browsers on the lab network.

**Deliverable:**

- Minimal extension with NanoHTTPD serving "Hello from Karoo" on port 8080
- Access confirmed from browser on separate device

**If this fails:** Fall back to client-push architecture (see Section 11 — Risk Register)

### Phase 2: Validate SDK Data Access (Day 2-3)

**Objective:** Confirm real-time data streaming from KarooSystem.

**Deliverable:**

- Extension subscribes to power, HR, cadence
- Data logged to Android logcat
- Data accessible via HTTP GET `/api/status`

### Phase 3: WebSocket Integration (Day 3-4)

**Objective:** Confirm WebSocket streaming from Karoo to browser.

**Deliverable:**

- Browser connects to `ws://<karoo-ip>:8080/live`
- Live power/HR/cadence values update in browser every second

### Phase 4: Dashboard MVP (Day 4-7)

**Objective:** Functional coach dashboard with all core metrics.

**Deliverable:**

- Coach view with power, HR, cadence, speed, time, distance
- Zone colouring
- Auto-reconnection on WebSocket drop
- Multi-client support verified

### Phase 5: Polish and Lab Integration (Week 2)

**Deliverables:**

- Athlete view (large numbers)
- Power graph (rolling 5-minute chart)
- Lap detection and display
- QR code display on Karoo for easy URL sharing
- Performance testing (battery impact, latency measurement)
- Documentation for lab staff

---

## 11. Risk Register

| ID | Risk | Likelihood | Impact | Mitigation |
| --- | --- | --- | --- | --- |
| R1 | Karoo OS blocks server sockets for sideloaded apps | Low | Critical | **Fallback:** Flip architecture — extension pushes data as WebSocket CLIENT to a lab server (Node.js/Python on a PC). Lab server then serves dashboards. Requires one extra machine but is guaranteed to work. |
| R2 | Background service killed during ride | Low | High | Follow SDK lifecycle patterns. Use Android foreground service with notification. Test extensively during rides. |
| R3 | Wi-Fi disconnects during ride | Very Low (lab) | Medium | Karoo is stationary on trainer. Use auto-reconnect in WebSocket client. Buffer last-known values. |
| R4 | SDK data stream frequency too low | Low | Medium | Test actual update rates. If <1Hz, interpolate on dashboard side. |
| R5 | SDK version changes break extension | Low | Medium | Pin SDK version. Test before updating Karoo firmware. |
| R6 | Port 8080 conflicts with another process | Very Low | Low | Make port configurable. Try 8081, 9090 as alternatives. |
| R7 | Multiple Karoo devices in lab create confusion | Low | Low | Display Karoo device name / athlete name in dashboard. Use different port per device if needed. |

### Fallback Architecture (if R1 materialises)

```
┌─────────────┐                          ┌──────────────────┐
│  Karoo       │ ── WebSocket CLIENT ──▶ │  Lab PC Server   │
│  Extension   │   pushes data outbound  │  (Node.js/Python) │
│              │   to ws://labpc:9090    │  Port 9090       │
└─────────────┘                          └────────┬─────────┘
                                                  │ serves
                                                  ▼
                                         ┌────────────────┐
                                         │  Browser/TV    │
                                         │  Dashboard     │
                                         │  :3000         │
                                         └────────────────┘
```

This fallback is trivial to implement and works identically from the browser's perspective. The only difference is the data flows through a lab PC intermediary.

---

## 12. Future Enhancements

### Phase 2 Features (Post-MVP)

- **Workout target overlays** — display prescribed vs actual power/HR
- **Interval timer** — visual countdown synced to structured workouts
- **Historical comparison** — overlay current effort against previous tests
- **Session recording** — server-side logging of all data for post-session analysis
- **Multi-athlete view** — multiple Karoo devices feeding into a single dashboard
- **Integration with metabolic cart** — combine Karoo data with VO2/VCO2 data
- **Alert system** — flash screen / audio alert when athlete exceeds/drops below target zones
- **OBS overlay** — transparent overlay view for video recording/streaming

### Phase 3 Features (Long-term)

- **Trainer control** — use Karoo SDK to relay commands to KICKR via the Karoo's FTMS connection
- **Mobile app** — native iOS/Android app instead of browser (if needed)
- **Cloud sync** — push session data to Braven's cloud platform

---

## 13. Appendices

### Appendix A: Power Zone Configuration

The dashboard should support configurable power zones. Default (Coggan model):

| Zone | Name | % FTP |
| --- | --- | --- |
| Z1 | Active Recovery | <55% |
| Z2 | Endurance | 56-75% |
| Z3 | Tempo | 76-90% |
| Z4 | Threshold | 91-105% |
| Z5 | VO2max | 106-120% |
| Z6 | Anaerobic | 121-150% |
| Z7 | Neuromuscular | >150% |

### Appendix B: Heart Rate Zone Configuration

| Zone | Name | % Max HR |
| --- | --- | --- |
| Z1 | Recovery | <60% |
| Z2 | Aerobic | 60-70% |
| Z3 | Tempo | 70-80% |
| Z4 | Threshold | 80-90% |
| Z5 | VO2max | 90-100% |

### Appendix C: Useful ADB Commands

```bash
# List connected devices
adb devices

# Install APK
adb install -r app-debug.apk

# Uninstall
adb uninstall com.braven.karoodashboard

# View logs (filtered)
adb logcat | grep -E "Braven|karoo-sdk"

# Get Karoo IP address
adb shell ip addr show wlan0

# Forward port (access Karoo server from dev machine even without same Wi-Fi)
adb forward tcp:8080 tcp:8080
# Then access <http://localhost:8080> on dev machine

# Wireless ADB setup
adb tcpip 5555
adb connect <karoo-ip>:5555

# Screenshot
adb exec-out screencap -p > karoo-screenshot.png

# File transfer
adb push local-file.txt /sdcard/
adb pull /sdcard/remote-file.txt ./
```

### Appendix D: Community Karoo Extensions (Reference)

These open-source extensions demonstrate SDK patterns your engineers can study:

| Extension | What It Does | Useful For |
| --- | --- | --- |
| karoo-headwind | Fetches external weather data, displays wind direction | Network requests, custom data fields |
| karoo-reminder | Background alerts during rides | Background service patterns, notifications |
| Various zone extensions | Custom power/HR zone displays | Data consumption, custom rendering |

> Search GitHub for `karoo-ext` or `karoo-sdk` to find community examples.
> 

### Appendix E: Transparency Notes

| Statement in This Document | Confidence Level | Notes |
| --- | --- | --- |
| Karoo SDK provides real-time data streaming | ✅ High | Confirmed from SDK source |
| Extensions can be sideloaded via ADB | ✅ High | Confirmed from SDK documentation |
| NanoHTTPD can run inside a Karoo extension | ⚠️ High (not 100%) | Standard Android capability; no evidence of restrictions; needs PoC validation |
| WebSocket server can serve external browsers | ⚠️ High (not 100%) | Depends on Karoo OS not blocking inbound connections; needs PoC validation |
| Exact SdkDataType enum values | ⚠️ Medium | Based on SDK source review; engineers should verify against actual compiled SDK |
| Background service persists during ride | ⚠️ High | SDK is designed for this; follow their service lifecycle patterns |

---

**End of Technical Specification**

**Next Action:** Engineering team to begin Phase 1 — PoC validation (estimated 1-2 days to confirm feasibility).

---