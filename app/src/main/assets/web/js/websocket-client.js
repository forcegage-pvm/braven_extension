/**
 * Braven Lab Dashboard — WebSocket Client
 *
 * Manages the WebSocket connection to the Karoo extension server.
 * Auto-reconnects on disconnect with exponential backoff.
 */
class BravenWebSocketClient {
    constructor() {
        this._ws = null;
        this._dataCallbacks = [];
        this._statusCallbacks = [];
        this._reconnectDelay = 1000;
        this._maxReconnectDelay = 10000;
        this._currentDelay = this._reconnectDelay;
        this._reconnectTimer = null;
        this._isConnected = false;
    }

    /**
     * Register a callback for incoming ride data.
     * @param {function(Object)} callback
     */
    onData(callback) {
        this._dataCallbacks.push(callback);
    }

    /**
     * Register a callback for connection status changes.
     * @param {function(boolean)} callback
     */
    onStatusChange(callback) {
        this._statusCallbacks.push(callback);
    }

    /**
     * Determine the WebSocket URL based on the current page location.
     * Uses the same host:port as the HTTP connection.
     */
    _getWebSocketUrl() {
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        return `${protocol}//${window.location.host}/live`;
    }

    /**
     * Establish WebSocket connection.
     */
    connect() {
        if (this._ws && (this._ws.readyState === WebSocket.CONNECTING || this._ws.readyState === WebSocket.OPEN)) {
            return;
        }

        const url = this._getWebSocketUrl();
        console.log(`[BravenWS] Connecting to ${url}`);

        try {
            this._ws = new WebSocket(url);
        } catch (e) {
            console.error('[BravenWS] Failed to create WebSocket:', e);
            this._scheduleReconnect();
            return;
        }

        this._ws.onopen = () => {
            console.log('[BravenWS] Connected');
            this._isConnected = true;
            this._currentDelay = this._reconnectDelay;
            this._notifyStatus(true);
        };

        this._ws.onmessage = (event) => {
            try {
                const data = JSON.parse(event.data);
                this._dataCallbacks.forEach(cb => cb(data));
            } catch (e) {
                console.warn('[BravenWS] Failed to parse message:', e);
            }
        };

        this._ws.onclose = (event) => {
            console.log(`[BravenWS] Disconnected (code=${event.code})`);
            this._isConnected = false;
            this._notifyStatus(false);
            this._scheduleReconnect();
        };

        this._ws.onerror = (error) => {
            console.error('[BravenWS] Error:', error);
        };
    }

    /**
     * Disconnect and stop reconnecting.
     */
    disconnect() {
        if (this._reconnectTimer) {
            clearTimeout(this._reconnectTimer);
            this._reconnectTimer = null;
        }
        if (this._ws) {
            this._ws.close();
            this._ws = null;
        }
        this._isConnected = false;
        this._notifyStatus(false);
    }

    /**
     * Schedule automatic reconnection with exponential backoff.
     */
    _scheduleReconnect() {
        if (this._reconnectTimer) return;

        console.log(`[BravenWS] Reconnecting in ${this._currentDelay}ms`);
        this._reconnectTimer = setTimeout(() => {
            this._reconnectTimer = null;
            this.connect();
        }, this._currentDelay);

        // Exponential backoff, capped
        this._currentDelay = Math.min(this._currentDelay * 1.5, this._maxReconnectDelay);
    }

    _notifyStatus(connected) {
        this._statusCallbacks.forEach(cb => cb(connected));
    }
}
