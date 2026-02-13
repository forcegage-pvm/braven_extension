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
    private val onMarkLap: (() -> Unit)? = null,
    private val onLactateUpdate: ((Double) -> Unit)? = null,
    // Trainer control callbacks
    private val onTrainerScan: (() -> Unit)? = null,
    private val onTrainerConnect: ((String) -> Unit)? = null,
    private val onTrainerSetPower: ((Int) -> Unit)? = null,
    private val onTrainerDisconnect: (() -> Unit)? = null,
    private val onTrainerStatus: (() -> String)? = null,
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

        // Handle CORS preflight requests
        if (session.method == Method.OPTIONS) {
            return NanoHTTPD.newFixedLengthResponse(
                NanoHTTPD.Response.Status.OK,
                NanoHTTPD.MIME_PLAINTEXT,
                "",
            ).also {
                it.addHeader("Access-Control-Allow-Origin", "*")
                it.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
                it.addHeader("Access-Control-Allow-Headers", "Content-Type")
            }
        }

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
            uri == "/api/discovery" -> {
                // Discovery endpoint for lab displays to find the Karoo
                val discoveryJson = """{"service":"braven-dashboard","version":"1.0","port":$port}"""
                return NanoHTTPD.newFixedLengthResponse(
                    NanoHTTPD.Response.Status.OK,
                    "application/json",
                    discoveryJson,
                ).also {
                    it.addHeader("Access-Control-Allow-Origin", "*")
                }
            }
            uri == "/api/lap" && session.method == Method.POST -> {
                // Trigger a lap mark on the Karoo
                return if (onMarkLap != null) {
                    Timber.i("WebServer: Lap mark requested via API")
                    onMarkLap.invoke()
                    NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.OK,
                        "application/json",
                        """{"success":true,"message":"Lap marked"}""",
                    ).also {
                        it.addHeader("Access-Control-Allow-Origin", "*")
                    }
                } else {
                    NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE,
                        "application/json",
                        """{"success":false,"message":"Lap marking not available"}""",
                    ).also {
                        it.addHeader("Access-Control-Allow-Origin", "*")
                    }
                }
            }
            uri == "/api/lactate" && session.method == Method.POST -> {
                // Submit a lactate measurement from the lab
                return try {
                    // NanoHTTPD requires parseBody to read POST content
                    val bodyFiles = HashMap<String, String>()
                    session.parseBody(bodyFiles)
                    val body = bodyFiles["postData"] ?: ""

                    // Simple JSON parsing for {"value": 1.2}
                    val valueMatch = Regex(""""value"\s*:\s*([\d.]+)""").find(body)
                    val lactateValue = valueMatch?.groupValues?.get(1)?.toDoubleOrNull()

                    if (lactateValue != null && lactateValue in 0.0..50.0) {
                        Timber.i("WebServer: Lactate submitted: $lactateValue mmol/L")
                        onLactateUpdate?.invoke(lactateValue)
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.OK,
                            "application/json",
                            """{"success":true,"value":$lactateValue,"message":"Lactate recorded"}""",
                        ).also {
                            it.addHeader("Access-Control-Allow-Origin", "*")
                        }
                    } else {
                        NanoHTTPD.newFixedLengthResponse(
                            NanoHTTPD.Response.Status.BAD_REQUEST,
                            "application/json",
                            """{"success":false,"message":"Invalid lactate value. Send {\"value\": <number>} with 0-50 range."}""",
                        ).also {
                            it.addHeader("Access-Control-Allow-Origin", "*")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WebServer: Error parsing lactate request")
                    NanoHTTPD.newFixedLengthResponse(
                        NanoHTTPD.Response.Status.INTERNAL_ERROR,
                        "application/json",
                        """{"success":false,"message":"${e.message}"}""",
                    ).also {
                        it.addHeader("Access-Control-Allow-Origin", "*")
                    }
                }
            }

            // ─── Trainer Control Endpoints ───────────────────
            uri == "/api/trainer/scan" && session.method == Method.POST -> {
                return if (onTrainerScan != null) {
                    Timber.i("WebServer: Trainer scan requested")
                    onTrainerScan.invoke()
                    jsonResponse("""{"success":true,"message":"Scanning for trainers"}""")
                } else {
                    jsonResponse("""{"success":false,"message":"Trainer control not available"}""", NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE)
                }
            }
            uri == "/api/trainer/connect" && session.method == Method.POST -> {
                return try {
                    val bodyFiles = HashMap<String, String>()
                    session.parseBody(bodyFiles)
                    val body = bodyFiles["postData"] ?: ""
                    val addressMatch = Regex(""""address"\s*:\s*"([^"]+)"""").find(body)
                    val address = addressMatch?.groupValues?.get(1)

                    if (address != null && onTrainerConnect != null) {
                        Timber.i("WebServer: Trainer connect requested: $address")
                        onTrainerConnect.invoke(address)
                        jsonResponse("""{"success":true,"message":"Connecting to trainer"}""")
                    } else {
                        jsonResponse("""{"success":false,"message":"Missing address or trainer control not available"}""", NanoHTTPD.Response.Status.BAD_REQUEST)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WebServer: Error parsing trainer connect request")
                    jsonResponse("""{"success":false,"message":"${e.message}"}""", NanoHTTPD.Response.Status.INTERNAL_ERROR)
                }
            }
            uri == "/api/trainer/power" && session.method == Method.POST -> {
                return try {
                    val bodyFiles = HashMap<String, String>()
                    session.parseBody(bodyFiles)
                    val body = bodyFiles["postData"] ?: ""
                    val wattsMatch = Regex(""""watts"\s*:\s*(\d+)""").find(body)
                    val watts = wattsMatch?.groupValues?.get(1)?.toIntOrNull()

                    if (watts != null && watts in 0..2000 && onTrainerSetPower != null) {
                        Timber.i("WebServer: Trainer target power: $watts W")
                        onTrainerSetPower.invoke(watts)
                        jsonResponse("""{"success":true,"watts":$watts,"message":"Target power set"}""")
                    } else {
                        jsonResponse("""{"success":false,"message":"Invalid watts (0-2000) or trainer not available"}""", NanoHTTPD.Response.Status.BAD_REQUEST)
                    }
                } catch (e: Exception) {
                    Timber.e(e, "WebServer: Error parsing trainer power request")
                    jsonResponse("""{"success":false,"message":"${e.message}"}""", NanoHTTPD.Response.Status.INTERNAL_ERROR)
                }
            }
            uri == "/api/trainer/disconnect" && session.method == Method.POST -> {
                return if (onTrainerDisconnect != null) {
                    Timber.i("WebServer: Trainer disconnect requested")
                    onTrainerDisconnect.invoke()
                    jsonResponse("""{"success":true,"message":"Trainer disconnected"}""")
                } else {
                    jsonResponse("""{"success":false,"message":"Trainer control not available"}""", NanoHTTPD.Response.Status.SERVICE_UNAVAILABLE)
                }
            }
            uri == "/api/trainer/status" -> {
                return if (onTrainerStatus != null) {
                    jsonResponse(onTrainerStatus.invoke())
                } else {
                    jsonResponse("""{"state":"UNAVAILABLE"}""")
                }
            }
            uri == "/discovery" || uri == "/discovery.html" -> "web/discovery.html"
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

    private fun jsonResponse(
        json: String,
        status: NanoHTTPD.Response.Status = NanoHTTPD.Response.Status.OK,
    ): NanoHTTPD.Response {
        return NanoHTTPD.newFixedLengthResponse(status, "application/json", json).also {
            it.addHeader("Access-Control-Allow-Origin", "*")
        }
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
