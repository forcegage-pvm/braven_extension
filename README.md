# Braven Lab Dashboard — Karoo Extension

A Hammerhead Karoo extension that streams live ride data (power, heart rate, cadence, speed, GPS, etc.) over Wi-Fi via an embedded HTTP + WebSocket server. Lab coaches and athletes connect from any browser on the same network to view real-time dashboards.

## Architecture

```
┌──────────────┐  WebSocket /live   ┌─────────────────┐
│  Karoo Device │ ──────────────────▶│  Browser (Coach) │
│  (Extension)  │  HTTP /coach      │  Browser (Athlete)│
│  Port 8080    │ ──────────────────▶│  Browser (Index)  │
└──────────────┘                    └─────────────────┘
```

- **Extension Service** — `BravenDashboardExtension` (bound by Karoo OS via `KAROO_EXTENSION` intent)
- **Data Collector** — subscribes to Karoo `DataType.Type.*` streams and aggregates into a `SessionState` flow
- **Web Server** — NanoHTTPD + NanoWSD serving static HTML/CSS/JS from Android assets and broadcasting JSON via WebSocket

## Dashboard Views

| View     | URL                                 | Description                                                |
| -------- | ----------------------------------- | ---------------------------------------------------------- |
| Index    | `http://<karoo-ip>:8080/`           | 3×3 grid with all metrics                                  |
| Coach    | `http://<karoo-ip>:8080/coach`      | Detailed view with GPS coordinates                         |
| Athlete  | `http://<karoo-ip>:8080/athlete`    | Large glanceable metrics (power, HR, cadence, speed, time) |
| REST API | `http://<karoo-ip>:8080/api/status` | JSON snapshot of current state                             |

## Data Fields

Power (W), Heart Rate (BPM), Cadence (RPM), Speed (km/h), Elapsed Time, Distance (km), Elevation (m), Grade (%), Temperature (°C), GPS Latitude/Longitude.

## Tech Stack

- **Karoo SDK**: `io.hammerhead:karoo-ext:1.1.8` (GitHub Packages)
- **HTTP/WS Server**: NanoHTTPD 2.3.1 + nanohttpd-websocket
- **Language**: Kotlin 2.0.21, Android SDK 34, minSdk 26
- **On-device UI**: Jetpack Compose (shows dashboard URL on Karoo screen)
- **Build**: Gradle 8.9, AGP 8.7.3

## Prerequisites

- Android SDK (API 34)
- JDK 17
- GitHub Personal Access Token with `read:packages` scope (for karoo-ext)

## Setup

1. Clone the repository
2. Copy `gradle.properties.example` values into `gradle.properties` with your GitHub credentials:
   ```properties
   gpr.user=YOUR_GITHUB_USERNAME
   gpr.key=YOUR_GITHUB_PAT
   ```
3. Create `local.properties` with your Android SDK path:
   ```properties
   sdk.dir=C:\\Android
   ```
4. Build:
   ```bash
   ./gradlew assembleDebug
   ```

## Install on Karoo

```bash
adb install app/build/outputs/apk/debug/app-debug.apk
```

The extension auto-starts when Karoo OS binds to it. Open the app on the Karoo to see the dashboard URL, then navigate to it from any browser on the same Wi-Fi.

## Project Structure

```
app/src/main/
├── kotlin/com/braven/karoodashboard/
│   ├── MainActivity.kt                 # Compose UI (dashboard URL display)
│   ├── data/
│   │   ├── SessionState.kt             # Ride data model + JSON serialization
│   │   └── DataCollector.kt            # Karoo data stream aggregator
│   ├── extension/
│   │   ├── BravenDashboardExtension.kt # KarooExtension service entry point
│   │   └── Extensions.kt              # streamDataFlow() / consumerFlow() helpers
│   └── server/
│       ├── WebServer.kt               # NanoWSD HTTP + WebSocket server
│       └── IpAddressUtil.kt           # Wi-Fi IP address utility
├── assets/web/
│   ├── index.html / coach.html / athlete.html
│   ├── css/dashboard.css
│   └── js/websocket-client.js / dashboard-renderer.js
└── res/
    ├── drawable/ic_braven.xml
    ├── values/strings.xml, themes.xml
    └── xml/extension_info.xml
```

## License

Proprietary — Braven Performance Lab.
