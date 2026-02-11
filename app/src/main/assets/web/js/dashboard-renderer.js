/**
 * Braven Lab Dashboard — Dashboard Renderer
 *
 * Updates the DOM elements with live ride data received from WebSocket.
 * Works across index.html, coach.html, and athlete.html views.
 */
class DashboardRenderer {
    constructor() {
        // Cache DOM references (may be null if element not present on page)
        this._els = {
            power: document.getElementById('power'),
            heartRate: document.getElementById('heartRate'),
            cadence: document.getElementById('cadence'),
            speed: document.getElementById('speed'),
            elapsedTime: document.getElementById('elapsedTime'),
            distance: document.getElementById('distance'),
            elevation: document.getElementById('elevation'),
            grade: document.getElementById('grade'),
            temperature: document.getElementById('temperature'),
            latitude: document.getElementById('latitude'),
            longitude: document.getElementById('longitude'),
            lastUpdate: document.getElementById('lastUpdate'),
        };

        this._statusDot = null;
        this._statusText = null;

        const statusEl = document.getElementById('connectionStatus');
        if (statusEl) {
            this._statusDot = statusEl.querySelector('.status-dot');
            this._statusText = statusEl.querySelector('.status-text');
        }
    }

    /**
     * Update all displayed metrics with new data.
     * @param {Object} data — parsed JSON from WebSocket
     */
    update(data) {
        this._setValue('power', data.power);
        this._setValue('heartRate', data.heartRate);
        this._setValue('cadence', data.cadence);
        this._setValue('speed', data.speed);
        this._setValue('distance', data.distance);
        this._setValue('elevation', data.elevation);
        this._setValue('grade', data.grade);
        this._setValue('temperature', data.temperature);

        // Elapsed time formatted as HH:MM:SS or MM:SS
        if (this._els.elapsedTime && data.elapsedTime !== undefined) {
            this._els.elapsedTime.textContent = this._formatTime(data.elapsedTime);
            this._animateUpdate(this._els.elapsedTime);
        }

        // GPS coordinates (coach view only)
        if (this._els.latitude && data.latitude !== undefined) {
            this._els.latitude.textContent = data.latitude.toFixed(6);
        }
        if (this._els.longitude && data.longitude !== undefined) {
            this._els.longitude.textContent = data.longitude.toFixed(6);
        }

        // Last update timestamp
        if (this._els.lastUpdate) {
            const now = new Date();
            this._els.lastUpdate.textContent = now.toLocaleTimeString();
        }
    }

    /**
     * Update the connection status indicator.
     * @param {boolean} connected
     */
    setConnectionStatus(connected) {
        if (this._statusDot) {
            this._statusDot.classList.toggle('connected', connected);
            this._statusDot.classList.toggle('disconnected', !connected);
        }
        if (this._statusText) {
            this._statusText.textContent = connected ? 'Live' : 'Disconnected';
        }
    }

    /**
     * Set a metric element's text content with animation.
     */
    _setValue(key, value) {
        const el = this._els[key];
        if (!el || value === undefined || value === null) return;

        const formatted = typeof value === 'number'
            ? (Number.isInteger(value) ? value.toString() : value.toFixed(1))
            : value.toString();

        if (el.textContent !== formatted) {
            el.textContent = formatted;
            this._animateUpdate(el);
        }
    }

    /**
     * Brief pulse animation when a value changes.
     */
    _animateUpdate(el) {
        el.classList.remove('updating');
        // Force reflow to restart animation
        void el.offsetWidth;
        el.classList.add('updating');
    }

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

        if (h > 0) {
            return `${h}:${pad(m)}:${pad(s)}`;
        }
        return `${pad(m)}:${pad(s)}`;
    }
}
