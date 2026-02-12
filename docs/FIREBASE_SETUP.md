# Firebase Cloud Discovery Setup

This guide explains how to set up Firebase Realtime Database for automatic Karoo IP discovery across any network.

## Why Firebase?

The Karoo's IP address changes each time it connects to WiFi. Firebase provides a simple cloud-based solution:

1. **Karoo** → Uploads its current IP to Firebase every 30 seconds
2. **Lab Display** → Fetches the IP from Firebase and auto-connects

This works from anywhere - you don't need to be on the same network as the Karoo.

## Step 1: Create Firebase Project

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Click **"Create a project"**
3. Name it something like `braven-lab-dashboard`
4. Disable Google Analytics (not needed)
5. Click **"Create project"**

## Step 2: Create Realtime Database

1. In your Firebase project, click **"Build"** → **"Realtime Database"**
2. Click **"Create Database"**
3. Choose any location (US is fine)
4. Select **"Start in test mode"** (we'll fix security later)
5. Click **"Enable"**

Your database URL will look like: `https://braven-lab-dashboard-default-rtdb.firebaseio.com`

## Step 3: Configure Security Rules

For a lab environment, you can keep it simple:

```json
{
  "rules": {
    "devices": {
      ".read": true,
      ".write": true
    }
  }
}
```

For production, consider:

- Allowing write only from specific IPs
- Adding authentication
- Setting up rate limiting

## Step 4: Configure the Karoo Extension

Edit `app/build.gradle.kts` and set your Firebase URL:

```kotlin
defaultConfig {
    // ... other config ...

    buildConfigField("String", "FIREBASE_URL", "\"https://your-project.firebaseio.com\"")
}
```

Then rebuild and deploy:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Step 5: Access the Discovery Page

The discovery page is hosted at:

**GitHub Pages URL:**

```
https://forcegage-pvm.github.io/braven_extension/
```

Or open `docs/index.html` locally.

### First-time setup on the discovery page:

1. Enter your Firebase Database URL
2. Enter your device ID (default: `braven-karoo`)
3. Click Connect

The page will:

- Save your settings in browser localStorage
- Auto-refresh every 10 seconds
- Auto-redirect to Coach view when Karoo is found

## How It Works

### Karoo Extension → Firebase

Every 30 seconds, the extension uploads:

```json
{
  "ip": "192.168.8.123",
  "port": 8080,
  "url": "http://192.168.8.123:8080",
  "timestamp": 1739376000000,
  "status": "online"
}
```

To: `https://your-project.firebaseio.com/devices/braven-karoo.json`

### Discovery Page → Firebase → Karoo

1. Fetches from Firebase: `GET /devices/braven-karoo.json`
2. Checks if data is fresh (< 2 minutes old)
3. Shows status (Online/Offline)
4. Auto-redirects to Karoo dashboard

## Multiple Karoo Devices

You can monitor multiple Karoo devices by giving each a unique device ID:

In `BravenDashboardExtension.kt`:

```kotlin
networkDiscovery = NetworkDiscoveryService(
    context = applicationContext,
    httpPort = SERVER_PORT,
    firebaseUrl = BuildConfig.FIREBASE_URL.ifEmpty { null },
    deviceId = "karoo-station-1"  // Unique ID
)
```

Then in the discovery page, enter the corresponding device ID.

## Troubleshooting

### "Karoo Offline" even though device is running

1. Check WiFi - Karoo needs internet access to reach Firebase
2. Check logs: `adb logcat | grep -i "NetworkDiscovery\|Firebase"`
3. Verify Firebase URL is correct in build.gradle.kts
4. Check Firebase Console → Realtime Database to see if data appears

### "Connection Error" on discovery page

1. Check Firebase Database URL is correct
2. Ensure database rules allow read access
3. Check browser console for detailed error
4. Try opening Firebase URL directly in browser

### Data not updating

1. Ensure Karoo extension is running (check notification)
2. Check Firebase Console → Realtime Database → devices
3. The `timestamp` field should update every 30 seconds

## Cost

Firebase Realtime Database free tier includes:

- 1 GB storage
- 10 GB/month download
- 100 simultaneous connections

This is more than enough for lab use - a single Karoo uploading every 30 seconds uses approximately:

- Storage: < 1 KB
- Bandwidth: ~1 MB/month
