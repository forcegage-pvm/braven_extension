/**
 * Braven Lab Dashboard — Dashboard Renderer (Tailwind Edition)
 *
 * Updates the DOM elements with live ride data received from WebSocket.
 * Handles zone badges, progress bars, cadence histogram,
 * core-temp trend tracking, battery display, and connection status.
 */
class DashboardRenderer {
    constructor() {
        // Cache DOM references — null-safe for multi-page support
        this._els = {
            // Header
            connectionBadge: document.getElementById('connectionBadge'),
            pingDot: document.getElementById('pingDot'),
            solidDot: document.getElementById('solidDot'),
            connectionText: document.getElementById('connectionText'),
            elapsedTime: document.getElementById('elapsedTime'),
            batteryPercent: document.getElementById('batteryPercent'),
            batteryIcon: document.getElementById('batteryIcon'),

            // Power card
            power: document.getElementById('power'),
            powerZoneBadge: document.getElementById('powerZoneBadge'),
            power3sAvg: document.getElementById('power3sAvg'),
            powerBar: document.getElementById('powerBar'),

            // Heart Rate card
            heartRate: document.getElementById('heartRate'),
            maxHeartRate: document.getElementById('maxHeartRate'),
            hrBar: document.getElementById('hrBar'),
            hrLiveBadge: document.getElementById('hrLiveBadge'),

            // Cadence card
            cadence: document.getElementById('cadence'),
            cadenceBars: document.getElementById('cadenceBars'),

            // Speed card
            speed: document.getElementById('speed'),
            averageSpeed: document.getElementById('averageSpeed'),

            // Distance card
            distance: document.getElementById('distance'),
            elevation: document.getElementById('elevation'),

            // Core Temp card
            coreTemp: document.getElementById('coreTemp'),
            coreTempTrend: document.getElementById('coreTempTrend'),

            // Footer
            grade: document.getElementById('grade'),
            temperature: document.getElementById('temperature'),
            lastUpdate: document.getElementById('lastUpdate'),

            // Legacy (coach/athlete pages)
            latitude: document.getElementById('latitude'),
            longitude: document.getElementById('longitude'),
        };

        // Cadence rolling buffer for mini histogram (last 8 values)
        this._cadenceHistory = [];
        this._maxCadenceHistory = 8;

        // Core temp trend tracking
        this._prevCoreTemp = null;

        // FTP estimate for power bar (user can override; defaults to 300W)
        this._ftpEstimate = 300;

        // Max HR estimate for HR bar (defaults to 200)
        this._maxHrEstimate = 200;

        // Zone names
        this._zoneNames = ['', 'Z1', 'Z2', 'Z3', 'Z4', 'Z5', 'Z6', 'Z7'];
    }

    /**
     * Update all displayed metrics with new data.
     * @param {Object} data — parsed JSON from WebSocket
     */
    update(data) {
        this._updatePower(data);
        this._updateHeartRate(data);
        this._updateCadence(data);
        this._updateSpeed(data);
        this._updateDistance(data);
        this._updateCoreTemp(data);
        this._updateHeader(data);
        this._updateFooter(data);

        // Legacy GPS (coach/athlete views)
        if (this._els.latitude && data.latitude !== undefined) {
            this._els.latitude.textContent = data.latitude.toFixed(6);
        }
        if (this._els.longitude && data.longitude !== undefined) {
            this._els.longitude.textContent = data.longitude.toFixed(6);
        }
    }

    // ─── Power Card ──────────────────────────────────────────

    _updatePower(data) {
        if (this._els.power && data.power !== undefined) {
            this._els.power.textContent = data.power > 0 ? data.power : '--';
        }
        if (this._els.power3sAvg && data.power3sAvg !== undefined) {
            this._els.power3sAvg.textContent = data.power3sAvg > 0 ? `${data.power3sAvg} W` : '-- W';
        }
        if (this._els.powerZoneBadge && data.powerZone !== undefined) {
            const zoneName = this._zoneNames[data.powerZone] || '--';
            this._els.powerZoneBadge.textContent = data.powerZone > 0 ? `Zone ${data.powerZone}` : '--';
        }
        if (this._els.powerBar && data.power !== undefined) {
            const pct = Math.min(100, Math.max(0, (data.power / this._ftpEstimate) * 100));
            this._els.powerBar.style.width = `${pct.toFixed(0)}%`;
        }
    }

    // ─── Heart Rate Card ─────────────────────────────────────

    _updateHeartRate(data) {
        if (this._els.heartRate && data.heartRate !== undefined) {
            this._els.heartRate.textContent = data.heartRate > 0 ? data.heartRate : '--';
        }
        if (this._els.maxHeartRate && data.maxHeartRate !== undefined) {
            this._els.maxHeartRate.textContent = data.maxHeartRate > 0 ? `${data.maxHeartRate} BPM` : '-- BPM';
            // Update max HR estimate for progress bar
            if (data.maxHeartRate > 0) {
                this._maxHrEstimate = Math.max(this._maxHrEstimate, data.maxHeartRate + 10);
            }
        }
        if (this._els.hrBar && data.heartRate !== undefined) {
            const pct = Math.min(100, Math.max(0, (data.heartRate / this._maxHrEstimate) * 100));
            this._els.hrBar.style.width = `${pct.toFixed(0)}%`;
        }
    }

    // ─── Cadence Card ────────────────────────────────────────

    _updateCadence(data) {
        if (this._els.cadence && data.cadence !== undefined) {
            this._els.cadence.textContent = data.cadence > 0 ? data.cadence : '--';
        }

        // Update rolling histogram
        if (data.cadence !== undefined && data.cadence > 0) {
            this._cadenceHistory.push(data.cadence);
            if (this._cadenceHistory.length > this._maxCadenceHistory) {
                this._cadenceHistory.shift();
            }
            this._renderCadenceBars();
        }
    }

    _renderCadenceBars() {
        if (!this._els.cadenceBars) return;

        const max = Math.max(...this._cadenceHistory, 120); // At least 120 RPM scale
        const bars = this._els.cadenceBars.children;

        for (let i = 0; i < bars.length; i++) {
            const idx = this._cadenceHistory.length - bars.length + i;
            if (idx >= 0 && idx < this._cadenceHistory.length) {
                const pct = Math.max(10, (this._cadenceHistory[idx] / max) * 100);
                bars[i].style.height = `${pct.toFixed(0)}%`;
            } else {
                bars[i].style.height = '10%';
            }
        }
    }

    // ─── Speed Card ──────────────────────────────────────────

    _updateSpeed(data) {
        if (this._els.speed && data.speed !== undefined) {
            this._els.speed.textContent = data.speed > 0 ? data.speed.toFixed(1) : '--';
        }
        if (this._els.averageSpeed && data.averageSpeed !== undefined) {
            this._els.averageSpeed.textContent = data.averageSpeed > 0 ? data.averageSpeed.toFixed(1) : '--';
        }
    }

    // ─── Distance Card ───────────────────────────────────────

    _updateDistance(data) {
        if (this._els.distance && data.distance !== undefined) {
            this._els.distance.textContent = data.distance > 0 ? data.distance.toFixed(1) : '--';
        }
        if (this._els.elevation && data.elevation !== undefined) {
            this._els.elevation.textContent = data.elevation > 0 ? `${data.elevation.toFixed(0)} m` : '-- m';
        }
    }

    // ─── Core Temp Card ──────────────────────────────────────

    _updateCoreTemp(data) {
        if (this._els.coreTemp && data.coreTemp !== undefined) {
            if (data.coreTemp > 0) {
                this._els.coreTemp.textContent = data.coreTemp.toFixed(1);
            } else {
                this._els.coreTemp.textContent = '--';
            }
        }

        if (this._els.coreTempTrend && data.coreTemp !== undefined && data.coreTemp > 0) {
            if (this._prevCoreTemp !== null) {
                const delta = data.coreTemp - this._prevCoreTemp;
                const sign = delta >= 0 ? '+' : '';
                const iconName = delta > 0.05 ? 'arrow-up-right' : (delta < -0.05 ? 'arrow-down-right' : 'minus');
                const color = delta > 0.05 ? 'text-orange-400' : (delta < -0.05 ? 'text-blue-400' : 'text-neutral-400');

                this._els.coreTempTrend.className = `${color} flex items-center gap-1`;
                this._els.coreTempTrend.innerHTML = `${sign}${delta.toFixed(1)}° <i data-lucide="${iconName}" class="w-4 h-4"></i>`;

                // Re-initialize the new icon
                if (typeof lucide !== 'undefined') {
                    lucide.createIcons({ nodes: this._els.coreTempTrend.querySelectorAll('[data-lucide]') });
                }
            }
            this._prevCoreTemp = data.coreTemp;
        }
    }

    // ─── Header (Timer, Battery) ─────────────────────────────

    _updateHeader(data) {
        // Elapsed time
        if (this._els.elapsedTime && data.elapsedTime !== undefined) {
            this._els.elapsedTime.textContent = this._formatTime(data.elapsedTime);
        }

        // Battery
        if (this._els.batteryPercent && data.batteryPercent !== undefined) {
            if (data.batteryPercent >= 0) {
                this._els.batteryPercent.textContent = `${data.batteryPercent}%`;
            } else {
                this._els.batteryPercent.textContent = '--%';
            }
        }
    }

    // ─── Footer (Grade, Temp, Timestamp) ─────────────────────

    _updateFooter(data) {
        if (this._els.grade && data.grade !== undefined) {
            this._els.grade.textContent = `${data.grade.toFixed(1)}%`;
        }
        if (this._els.temperature && data.temperature !== undefined) {
            this._els.temperature.textContent = `${data.temperature.toFixed(1)}°C`;
        }
        if (this._els.lastUpdate) {
            this._els.lastUpdate.textContent = new Date().toLocaleTimeString();
        }
    }

    // ─── Connection Status ───────────────────────────────────

    /**
     * Update the connection status indicator in the header.
     * @param {boolean} connected
     */
    setConnectionStatus(connected) {
        const badge = this._els.connectionBadge;
        const ping = this._els.pingDot;
        const solid = this._els.solidDot;
        const text = this._els.connectionText;

        if (badge) {
            if (connected) {
                badge.className = 'flex items-center gap-2 px-3 py-1.5 rounded-full bg-green-500/10 border border-green-500/20';
            } else {
                badge.className = 'flex items-center gap-2 px-3 py-1.5 rounded-full bg-red-500/10 border border-red-500/20';
            }
        }
        if (ping) {
            ping.className = connected
                ? 'animate-ping absolute inline-flex h-full w-full rounded-full bg-green-400 opacity-75'
                : 'absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75';
        }
        if (solid) {
            solid.className = connected
                ? 'relative inline-flex rounded-full h-2 w-2 bg-green-500'
                : 'relative inline-flex rounded-full h-2 w-2 bg-red-500';
        }
        if (text) {
            text.className = connected
                ? 'text-sm font-medium text-green-500 tracking-wide uppercase'
                : 'text-sm font-medium text-red-500 tracking-wide uppercase';
            text.textContent = connected ? 'Live Stream' : 'Disconnected';
        }
    }

    // ─── Utilities ───────────────────────────────────────────

    /**
     * Format elapsed seconds as HH:MM:SS or MM:SS.
     * @param {number} totalSeconds
     * @returns {string}
     */
    _formatTime(totalSeconds) {
        const seconds = Math.floor(totalSeconds);
        const h = Math.floor(seconds / 3600);
        const m = Math.floor((seconds % 3600) / 60);
        const s = seconds % 60;
        const pad = (n) => n.toString().padStart(2, '0');
        return h > 0 ? `${h}:${pad(m)}:${pad(s)}` : `${pad(m)}:${pad(s)}`;
    }
}
