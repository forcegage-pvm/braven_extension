package com.braven.karoodashboard.server

import android.content.res.AssetManager
import com.braven.karoodashboard.data.DataCollector
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoWSD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP + WebSocket server running on the Karoo device.
 *
 * Serves static dashboard HTML/CSS/JS from Android assets and
 * streams live ride data via WebSocket to connected browsers.
 */
class WebServer(
    private val port: Int,
    private val assetManager: AssetManager,
    private val dataProvider: DataCollector,
) : NanoWSD(port) {

    private val connectedClients = CopyOnWriteArrayList<BravenWebSocket>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun openWebSocket(handshake: NanoHTTPD.IHTTPSession): WebSocket {
        Timber.d("WebServer: WebSocket connection from ${handshake.remoteIpAddress}")
        return BravenWebSocket(handshake)
    }

    override fun serve(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        val uri = session.uri ?: "/"
        Timber.d("WebServer: HTTP ${session.method} $uri")

        // Route to appropriate handler
        val assetPath = when {
            uri == "/" || uri == "/index.html" -> "web/index.html"
            uri == "/coach" || uri == "/coach.html" -> "web/coach.html"
            uri == "/athlete" || uri == "/athlete.html" -> "web/athlete.html"
            uri.startsWith("/css/") -> "web$uri"
            uri.startsWith("/js/") -> "web$uri"
            uri.startsWith("/fonts/") -> "web$uri"
            uri == "/live" -> return super.serve(session) // WebSocket upgrade
            uri == "/api/status" -> {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    dataProvider.currentState.value.toJson(),
                ).also {
                    it.addHeader("Access-Control-Allow-Origin", "*")
                }
            }
            else -> {
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.NOT_FOUND,
                    NanoHTTPD.MIME_PLAINTEXT,
                    "Not Found: $uri",
                )
            }
        }

        return try {
            val inputStream = assetManager.open(assetPath)
            val mimeType = getMimeType(assetPath)
            NanoHTTPD.newChunkedResponse(NanoHTTPD.Response.Status.OK, mimeType, inputStream).also {
                it.addHeader("Access-Control-Allow-Origin", "*")
                it.addHeader("Cache-Control", "no-cache")
            }
        } catch (e: IOException) {
            Timber.w("WebServer: Asset not found: $assetPath")
            NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.NOT_FOUND,
                NanoHTTPD.MIME_PLAINTEXT,
                "Asset not found: $assetPath",
            )
        }
    }

    /**
     * Start broadcasting session state changes to all connected WebSocket clients.
     */
    fun startBroadcasting() {
        scope.launch {
            Timber.i("WebServer: Broadcasting started")
            dataProvider.currentState.collect { state ->
                val json = state.toJson()
                val deadClients = mutableListOf<BravenWebSocket>()

                connectedClients.forEach { client ->
                    try {
                        client.send(json)
                    } catch (e: Exception) {
                        Timber.w("WebServer: Failed to send to client, removing")
                        deadClients.add(client)
                    }
                }

                deadClients.forEach { connectedClients.remove(it) }
            }
        }
    }

    /**
     * Stop the server and clean up resources.
     */
    fun stopServer() {
        Timber.i("WebServer: Stopping server")
        scope.cancel()
        connectedClients.clear()
        stop()
    }

    private fun getMimeType(path: String): String = when {
        path.endsWith(".html") -> "text/html"
        path.endsWith(".css") -> "text/css"
        path.endsWith(".js") -> "application/javascript"
        path.endsWith(".json") -> "application/json"
        path.endsWith(".png") -> "image/png"
        path.endsWith(".svg") -> "image/svg+xml"
        path.endsWith(".ico") -> "image/x-icon"
        path.endsWith(".ttf") -> "font/ttf"
        path.endsWith(".woff") -> "font/woff"
        path.endsWith(".woff2") -> "font/woff2"
        else -> "application/octet-stream"
    }

    /**
     * Individual WebSocket connection handler.
     */
    inner class BravenWebSocket(handshake: NanoHTTPD.IHTTPSession) : WebSocket(handshake) {

        override fun onOpen() {
            Timber.i("WebServer: Client connected (${connectedClients.size + 1} total)")
            connectedClients.add(this)
        }

        override fun onClose(
            code: WebSocketFrame.CloseCode?,
            reason: String?,
            initiatedByRemote: Boolean,
        ) {
            Timber.i("WebServer: Client disconnected: $reason")
            connectedClients.remove(this)
        }

        override fun onMessage(message: WebSocketFrame?) {
            // Client-to-server messages (future: zone config, athlete name, etc.)
            Timber.d("WebServer: Received message: ${message?.textPayload}")
        }

        override fun onPong(pong: WebSocketFrame?) {
            // Keep-alive acknowledgement
        }

        override fun onException(exception: IOException?) {
            Timber.w("WebServer: WebSocket error: ${exception?.message}")
            connectedClients.remove(this)
        }
    }
}
