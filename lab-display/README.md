# Braven Lab Display Setup

This folder contains files for setting up dedicated lab display screens that automatically connect to the Karoo.

## Quick Start

1. Copy `braven-discovery.html` to the lab display computer
2. Double-click to open in a web browser (Chrome recommended)
3. The page will automatically:
   - Search for the Karoo on the network
   - Find it via mDNS (`braven-karoo.local`) or IP scanning
   - Auto-redirect to the Coach view after 3 seconds

## Files

- **braven-discovery.html** - Auto-discovery page. Open this file to start monitoring.

## How It Works

The discovery page tries multiple methods to find the Karoo:

1. **Cached Address** - First checks if a previously found address still works
2. **mDNS** - Tries `http://braven-karoo.local:8080` (works on most networks)
3. **Network Scan** - Scans the local subnet (192.168.x.1-254) for the Braven server

## Configuration

Click "⚙️ Advanced Settings" on the discovery page to:

- Change the subnet prefix (default: 192.168.8)
- Change the port (default: 8080)
- Disable auto-redirect to Coach view

## Kiosk Mode (Full Screen)

For a dedicated display, run Chrome in kiosk mode:

**Windows:**

```
"C:\Program Files\Google\Chrome\Application\chrome.exe" --kiosk --app=file:///C:/path/to/braven-discovery.html
```

**macOS:**

```
open -a "Google Chrome" --args --kiosk --app=file:///path/to/braven-discovery.html
```

## Troubleshooting

**"Karoo Not Found"**

- Ensure the Karoo is powered on
- Check the Karoo is connected to the same WiFi network
- Look for the "Braven Dashboard" notification on the Karoo showing the IP
- Try entering the IP manually

**Slow Discovery**

- The mDNS method is fastest but requires network support
- IP scanning takes ~10-20 seconds to scan the full subnet
- Once found, the address is cached for instant reconnection

## Network Requirements

- Lab display and Karoo must be on the same local network
- Port 8080 must not be blocked by firewall
- For mDNS: multicast DNS must be enabled on the network (usually is by default)
