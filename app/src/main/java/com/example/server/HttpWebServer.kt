package com.example.server

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.data.model.GameApp
import com.example.data.model.RemoteInputEvent
import com.example.data.model.ServerStatus
import com.example.encoder.HardwareVideoEncoder
import com.example.service.SunshineAccessibilityService
import com.example.service.SunshineKeyboardService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * HTTP + WebSocket server.
 *
 * Trusted-LAN design: clients connect and are immediately authenticated (the
 * app is meant for a single user on a private Wi-Fi network).
 *
 * Robustness: every client has its own bounded outbound queue + writer thread,
 * so one slow/laggy client can never block the encoder broadcast thread. When a
 * client falls behind its queue is dropped and the browser re-syncs itself by
 * requesting a fresh keyframe.
 */
class HttpWebServer(
    private val port: Int,
    private val getAppsProvider: () -> List<GameApp>,
    private val getStatusProvider: () -> ServerStatus,
    private val onInputReceived: (RemoteInputEvent) -> Unit,
    private val onKeyFrameRequested: () -> Unit,
    private val context: Context? = null
) {
    companion object {
        private const val TAG = "HttpWebServer"
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val PACKET_TYPE_VIDEO: Byte = 0x01
        const val PACKET_TYPE_AUDIO: Byte = 0x02
        private const val OUT_QUEUE_FRAMES = 240 // ~4s at 60fps before we start dropping
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val isRunning = AtomicBoolean(false)
    private val activeWsClients = CopyOnWriteArrayList<WebSocketClient>()

    fun start(scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        serverJob = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = ServerSocket(port).apply {
                    reuseAddress = true
                }
                Log.i(TAG, "Sunshine Cast HTTP & WebSocket server started on port " + port)

                while (isRunning.get() && isActive) {
                    val clientSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: Exception) {
                        break
                    }
                    launch(Dispatchers.IO) {
                        handleClient(clientSocket)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error", e)
            } finally {
                stop()
            }
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping Sunshine Cast server")

        serverJob?.cancel()
        serverJob = null

        activeWsClients.forEach { it.close() }
        activeWsClients.clear()

        try {
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: " + e.message)
        }
        serverSocket = null
    }

    fun getClientCount(): Int = activeWsClients.count { it.isAuthenticated }

    fun broadcastVideoFrame(naluData: ByteArray, isKeyFrame: Boolean, ptsUs: Long) {
        if (activeWsClients.isEmpty()) return

        // 10-byte header + NALU payload
        val packet = ByteArray(10 + naluData.size)
        packet[0] = PACKET_TYPE_VIDEO
        packet[1] = if (isKeyFrame) 1 else 0

        val bb = ByteBuffer.wrap(packet, 2, 8)
        bb.putLong(ptsUs)

        System.arraycopy(naluData, 0, packet, 10, naluData.size)

        for (client in activeWsClients) {
            if (client.isAuthenticated) client.sendBinary(packet)
        }
    }

    fun broadcastAudioChunk(pcmData: ByteArray, ptsUs: Long) {
        if (activeWsClients.isEmpty()) return

        val packet = ByteArray(10 + pcmData.size)
        packet[0] = PACKET_TYPE_AUDIO
        packet[1] = 0

        val bb = ByteBuffer.wrap(packet, 2, 8)
        bb.putLong(ptsUs)

        System.arraycopy(pcmData, 0, packet, 10, pcmData.size)

        for (client in activeWsClients) {
            if (client.isAuthenticated) client.sendBinary(packet)
        }
    }

    fun broadcastText(text: String) {
        for (client in activeWsClients) {
            if (client.isAuthenticated) client.sendText(text)
        }
    }

    /** Tell every client whether input injection is currently possible on the phone. */
    fun broadcastInputStatus() {
        broadcastText(inputStatusJson())
    }

    private fun inputStatusJson(): String {
        return JSONObject().apply {
            put("type", "input_status")
            put("accessibility", SunshineAccessibilityService.isServiceActive)
            put("ime", SunshineKeyboardService.isServiceActive)
        }.toString()
    }

    // ------------------------------------------------------------------
    // HTTP layer
    // ------------------------------------------------------------------

    private fun handleClient(socket: Socket) {
        try {
            socket.tcpNoDelay = true
            val inputStream = socket.getInputStream()
            val outputStream = BufferedOutputStream(socket.getOutputStream())

            val requestLine = readLine(inputStream) ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val path = parts[1]

            // Read headers
            val headers = mutableMapOf<String, String>()
            while (true) {
                val line = readLine(inputStream) ?: break
                if (line.isEmpty()) break
                val colonIdx = line.indexOf(":")
                if (colonIdx > 0) {
                    headers[line.substring(0, colonIdx).trim().lowercase()] =
                        line.substring(colonIdx + 1).trim()
                }
            }

            // WebSocket Upgrade Request
            if (headers["upgrade"]?.equals("websocket", ignoreCase = true) == true) {
                val secKey = headers["sec-websocket-key"]
                if (secKey != null) {
                    upgradeToWebSocket(socket, inputStream, outputStream, secKey)
                    return
                }
            }

            // Standard HTTP Handlers
            when {
                path == "/" || path == "/index.html" -> {
                    val html = WebClientHtml.getHtml()
                    sendHttpResponse(outputStream, 200, "text/html; charset=utf-8", html.toByteArray())
                }
                path == "/js/jmuxer.min.js" || path == "/js/jmuxer.js" -> {
                    val jsBytes = try {
                        context?.assets?.open("jmuxer.min.js")?.use { it.readBytes() }
                    } catch (e: Exception) {
                        null
                    }
                    if (jsBytes != null) {
                        sendHttpResponse(outputStream, 200, "application/javascript; charset=utf-8", jsBytes)
                    } else {
                        sendHttpResponse(outputStream, 404, "text/plain", "Not Found".toByteArray())
                    }
                }
                path.startsWith("/api/apps") -> {
                    val apps = getAppsProvider()
                    val jsonArray = JSONArray()
                    apps.forEach { app ->
                        jsonArray.put(JSONObject().apply {
                            put("packageName", app.packageName)
                            put("appName", app.appName)
                            put("iconBase64", app.iconBase64)
                            put("isEmulator", app.isEmulator)
                            put("category", app.category)
                        })
                    }
                    sendHttpResponse(outputStream, 200, "application/json", jsonArray.toString().toByteArray())
                }
                path.startsWith("/api/status") -> {
                    val status = getStatusProvider()
                    val json = JSONObject().apply {
                        put("isRunning", status.isRunning)
                        put("isStreaming", status.isStreaming)
                        put("clientCount", getClientCount())
                        put("fps", status.currentFps)
                        put("bitrateMbps", status.currentBitrateMbps)
                        put("activeApp", status.activeAppName)
                    }
                    sendHttpResponse(outputStream, 200, "application/json", json.toString().toByteArray())
                }
                path.startsWith("/api/launch") -> {
                    var pkgToLaunch = ""
                    val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
                    if (contentLength > 0) {
                        val bodyBytes = ByteArray(contentLength)
                        var read = 0
                        while (read < contentLength) {
                            val r = inputStream.read(bodyBytes, read, contentLength - read)
                            if (r == -1) break
                            read += r
                        }
                        val bodyStr = String(bodyBytes, Charsets.UTF_8)
                        val json = try { JSONObject(bodyStr) } catch (e: Exception) { null }
                        pkgToLaunch = json?.optString("packageName") ?: ""
                    }
                    if (pkgToLaunch.isNotEmpty()) {
                        onInputReceived(RemoteInputEvent.LaunchApp(pkgToLaunch))
                        sendHttpResponse(outputStream, 200, "application/json", """{"success":true}""".toByteArray())
                    } else {
                        sendHttpResponse(outputStream, 400, "application/json", """{"error":"Missing packageName"}""".toByteArray())
                    }
                }
                else -> {
                    sendHttpResponse(outputStream, 404, "text/plain", "Not Found".toByteArray())
                }
            }

            socket.close()
        } catch (e: Exception) {
            Log.d(TAG, "Client handle ended: " + e.message)
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun sendHttpResponse(
        outputStream: OutputStream,
        statusCode: Int,
        contentType: String,
        body: ByteArray
    ) {
        val statusMsg = when (statusCode) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Unknown"
        }
        val header = "HTTP/1.1 $statusCode $statusMsg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Connection: close\r\n\r\n"

        outputStream.write(header.toByteArray(Charsets.US_ASCII))
        outputStream.write(body)
        outputStream.flush()
    }

    private fun readLine(inputStream: InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val c = inputStream.read()
            if (c == -1) return if (sb.isEmpty()) null else sb.toString()
            if (c == '\n'.code) break
            if (c != '\r'.code) sb.append(c.toChar())
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // WebSocket layer
    // ------------------------------------------------------------------

    private fun upgradeToWebSocket(
        socket: Socket,
        inputStream: InputStream,
        outputStream: BufferedOutputStream,
        secKey: String
    ) {
        val acceptKey = generateSecWebSocketAccept(secKey)
        val response = "HTTP/1.1 101 Switching Protocols\r\n" +
                "Upgrade: websocket\r\n" +
                "Connection: Upgrade\r\n" +
                "Sec-WebSocket-Accept: $acceptKey\r\n\r\n"

        outputStream.write(response.toByteArray(Charsets.US_ASCII))
        outputStream.flush()

        val wsClient = WebSocketClient(socket, inputStream, outputStream)
        wsClient.onMessage = { text -> handleWsMessage(wsClient, text) }
        wsClient.onAuthenticated = {
            Log.i(TAG, "WebSocket client authenticated")
            // Send cached sync frame / SPS-PPS so the decoder initializes instantly
            val syncFrame = HardwareVideoEncoder.latestSyncFrame
            if (syncFrame != null) {
                val packet = ByteArray(10 + syncFrame.size)
                packet[0] = PACKET_TYPE_VIDEO
                packet[1] = 1 // Keyframe
                val bb = ByteBuffer.wrap(packet, 2, 8)
                bb.putLong(System.nanoTime() / 1000)
                System.arraycopy(syncFrame, 0, packet, 10, syncFrame.size)
                wsClient.sendBinary(packet)
                Log.i(TAG, "Sent initial sync frame to new client (" + syncFrame.size + " bytes)")
            }
            onKeyFrameRequested()
            wsClient.sendText(inputStatusJson())
        }
        activeWsClients.add(wsClient)
        Log.i(TAG, "WebSocket client connected. Total clients: " + activeWsClients.size)

        // Trusted-LAN mode: clients are authenticated as soon as the handshake
        // completes, which immediately arms video + input for them.
        wsClient.authenticate()
        wsClient.onAuthenticated?.invoke()

        wsClient.listen {
            activeWsClients.remove(wsClient)
            Log.i(TAG, "WebSocket client disconnected. Total clients: " + activeWsClients.size)
        }
    }

    private fun handleWsMessage(client: WebSocketClient, text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "keyboard" -> {
                    val code = json.optString("code")
                    val key = json.optString("key")
                    val isDown = json.optBoolean("isDown", false)
                    // Calibrated tap target (from the browser key-map editor), if any
                    val tx: Float = if (json.has("x")) json.optDouble("x").toFloat() else -1f
                    val ty: Float = if (json.has("y")) json.optDouble("y").toFloat() else -1f
                    onInputReceived(RemoteInputEvent.Keyboard(code, key, isDown, emptyList(), tx, ty))
                }
                "mouse" -> {
                    val action = json.optString("action")
                    val button = json.optInt("button", 0)
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val mx = json.optDouble("movementX", 0.0).toFloat()
                    val my = json.optDouble("movementY", 0.0).toFloat()
                    val dy = json.optDouble("deltaY", 0.0).toFloat()
                    onInputReceived(RemoteInputEvent.Mouse(action, x, y, button, mx, my, dy))
                }
                "touch" -> {
                    val action = json.optString("action")
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val pointerId = json.optInt("pointerId", 0)
                    onInputReceived(RemoteInputEvent.Touch(action, x, y, pointerId))
                }
                "launch" -> {
                    val pkg = json.optString("packageName")
                    if (pkg.isNotEmpty()) {
                        onInputReceived(RemoteInputEvent.LaunchApp(pkg))
                    }
                }
                "control" -> {
                    val action = json.optString("action")
                    val value = json.optString("value")
                    if (action == "request_keyframe") {
                        onKeyFrameRequested()
                    } else {
                        onInputReceived(RemoteInputEvent.StreamControl(action, value))
                    }
                }
                "ping" -> {
                    client.sendText("""{"type":"pong"}""")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing WS message: " + e.message)
        }
    }

    private fun generateSecWebSocketAccept(key: String): String {
        val combined = key.trim() + WS_GUID
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(combined.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // --- INNER WEBSOCKET CLIENT HANDLER ---

    /**
     * One WebSocket client with a bounded outbound queue and a dedicated writer
     * thread. Producers (encoder / input) never block on the socket; if the
     * client can't keep up we drop the backlog - the browser requests a fresh
     * keyframe and recovers on its own.
     */
    private class WebSocketClient(
        private val socket: Socket,
        private val inputStream: InputStream,
        private val outputStream: OutputStream
    ) {
        private class Frame(val opcode: Int, val payload: ByteArray) {
            companion object {
                const val POISON = -1
            }
        }

        var onMessage: ((String) -> Unit)? = null
        var onAuthenticated: (() -> Unit)? = null

        @Volatile
        var isAuthenticated: Boolean = false
            private set

        private val isClosed = AtomicBoolean(false)
        private val outQueue = ArrayBlockingQueue<Frame>(OUT_QUEUE_FRAMES)
        private val writer = Thread {
            try {
                while (true) {
                    val f = outQueue.take()
                    if (f.opcode == Frame.POISON || isClosed.get()) break
                    try {
                        writeFrame(f.opcode, f.payload)
                    } catch (e: Exception) {
                        close()
                        break
                    }
                }
            } catch (e: InterruptedException) {
                // shutdown: socket closed elsewhere
            }
        }.apply {
            name = "ws-writer"
            isDaemon = true
            start()
        }

        fun authenticate() {
            isAuthenticated = true
        }

        fun listen(onDisconnect: () -> Unit) {
            try {
                while (!isClosed.get()) {
                    val b1 = inputStream.read()
                    if (b1 == -1) break

                    val opcode = b1 and 0x0F
                    val b2 = inputStream.read()
                    if (b2 == -1) break

                    val isMasked = (b2 and 0x80) != 0
                    var length = (b2 and 0x7F).toLong()

                    if (length == 126L) {
                        val byte1 = inputStream.read()
                        val byte2 = inputStream.read()
                        length = ((byte1 shl 8) or byte2).toLong()
                    } else if (length == 127L) {
                        var len = 0L
                        for (i in 0 until 8) {
                            len = (len shl 8) or inputStream.read().toLong()
                        }
                        length = len
                    }

                    val maskKey = ByteArray(4)
                    if (isMasked) {
                        inputStream.read(maskKey)
                    }

                    if (length > 10 * 1024 * 1024) { // 10MB sanity limit
                        break
                    }

                    val payload = ByteArray(length.toInt())
                    var totalRead = 0
                    while (totalRead < length) {
                        val r = inputStream.read(payload, totalRead, length.toInt() - totalRead)
                        if (r == -1) break
                        totalRead += r
                    }

                    if (isMasked) {
                        for (i in payload.indices) {
                            payload[i] = (payload[i].toInt() xor maskKey[i % 4].toInt()).toByte()
                        }
                    }

                    when (opcode) {
                        0x01 -> { // Text
                            val text = String(payload, Charsets.UTF_8)
                            onMessage?.invoke(text)
                        }
                        0x08 -> { // Close
                            break
                        }
                        0x09 -> { // Ping
                            enqueue(0x0A, payload, false)
                        }
                    }
                }
            } catch (e: Exception) {
                // Connection closed
            } finally {
                close()
                onDisconnect()
            }
        }

        fun sendBinary(data: ByteArray) {
            enqueue(0x02, data, true)
        }

        fun sendText(text: String) {
            enqueue(0x01, text.toByteArray(Charsets.UTF_8), false)
        }

        /**
         * Enqueue a frame. Video/audio (droppable=true) clear the backlog when the
         * queue is full so the stream re-syncs; control text just drops if full.
         */
        private fun enqueue(opcode: Int, payload: ByteArray, droppable: Boolean) {
            if (isClosed.get()) return
            val frame = Frame(opcode, payload)
            if (!outQueue.offer(frame)) {
                if (droppable) {
                    outQueue.clear()
                    outQueue.offer(frame)
                } // else: drop this frame, client will recover
            }
        }

        private fun writeFrame(opcode: Int, payload: ByteArray) {
            val out = outputStream
            out.write(0x80 or (opcode and 0x0F))

            val length = payload.size
            if (length <= 125) {
                out.write(length)
            } else if (length <= 65535) {
                out.write(126)
                out.write((length shr 8) and 0xFF)
                out.write(length and 0xFF)
            } else {
                out.write(127)
                for (i in 7 downTo 0) {
                    out.write((length.toLong() shr (i * 8)).toInt() and 0xFF)
                }
            }

            out.write(payload)
            out.flush()
        }

        fun close() {
            if (isClosed.getAndSet(true)) return
            try {
                outQueue.offer(Frame(Frame.POISON, ByteArray(0)))
            } catch (_: Exception) {
            }
            try {
                writer.interrupt()
            } catch (_: Exception) {
            }
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
