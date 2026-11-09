package com.example.server

import android.util.Base64
import android.util.Log
import com.example.data.model.GameApp
import com.example.data.model.RemoteInputEvent
import com.example.data.model.ServerStatus
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class HttpWebServer(
    private val port: Int,
    private val getAppsProvider: () -> List<GameApp>,
    private val getStatusProvider: () -> ServerStatus,
    private val onInputReceived: (RemoteInputEvent) -> Unit,
    private val onKeyFrameRequested: () -> Unit
) {
    companion object {
        private const val TAG = "HttpWebServer"
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val PACKET_TYPE_VIDEO: Byte = 0x01
        const val PACKET_TYPE_AUDIO: Byte = 0x02
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
                Log.i(TAG, "Sunshine Cast HTTP & WebSocket server started on port $port")

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
            Log.w(TAG, "Error closing server socket: ${e.message}")
        }
        serverSocket = null
    }

    fun getClientCount(): Int = activeWsClients.size

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
            client.sendBinary(packet)
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
            client.sendBinary(packet)
        }
    }

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
                path == "/api/apps" -> {
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
                path == "/api/status" -> {
                    val status = getStatusProvider()
                    val json = JSONObject().apply {
                        put("isRunning", status.isRunning)
                        put("isStreaming", status.isStreaming)
                        put("clientCount", activeWsClients.size)
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
            Log.d(TAG, "Client handle ended: ${e.message}")
            try { socket.close() } catch (_: Exception) {}
        }
    }

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

        val wsClient = WebSocketClient(socket, inputStream, outputStream) { event ->
            handleWsMessage(event)
        }
        activeWsClients.add(wsClient)
        Log.i(TAG, "WebSocket client connected. Total clients: ${activeWsClients.size}")

        // Send cached sync frame / SPS-PPS immediately so client decoder initializes at millisecond 0
        val syncFrame = com.example.encoder.HardwareVideoEncoder.latestSyncFrame
        if (syncFrame != null) {
            val packet = ByteArray(10 + syncFrame.size)
            packet[0] = PACKET_TYPE_VIDEO
            packet[1] = 1 // Keyframe
            val bb = ByteBuffer.wrap(packet, 2, 8)
            bb.putLong(System.nanoTime() / 1000)
            System.arraycopy(syncFrame, 0, packet, 10, syncFrame.size)
            wsClient.sendBinary(packet)
            Log.i(TAG, "Sent initial sync frame to new client (${syncFrame.size} bytes)")
        }

        // Send initial keyframe request
        onKeyFrameRequested()

        wsClient.listen {
            activeWsClients.remove(wsClient)
            Log.i(TAG, "WebSocket client disconnected. Total clients: ${activeWsClients.size}")
        }
    }

    private fun handleWsMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.optString("type")

            when (type) {
                "keyboard" -> {
                    val code = json.optString("code")
                    val key = json.optString("key")
                    val isDown = json.optBoolean("isDown", false)
                    onInputReceived(RemoteInputEvent.Keyboard(code, key, isDown))
                }
                "mouse" -> {
                    val action = json.optString("action")
                    val button = json.optInt("button", 0)
                    val x = json.optDouble("x", 0.0).toFloat()
                    val y = json.optDouble("y", 0.0).toFloat()
                    val mx = json.optDouble("movementX", 0.0).toFloat()
                    val my = json.optDouble("movementY", 0.0).toFloat()
                    onInputReceived(RemoteInputEvent.Mouse(action, x, y, button, mx, my))
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
                    broadcastText("""{"type":"pong"}""")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing WS message: ${e.message}")
        }
    }

    fun broadcastText(text: String) {
        for (client in activeWsClients) {
            client.sendText(text)
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
            404 -> "Not Found"
            400 -> "Bad Request"
            else -> "Unknown"
        }
        val header = "HTTP/1.1 $statusCode $statusMsg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${body.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
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

    private fun generateSecWebSocketAccept(key: String): String {
        val combined = key.trim() + WS_GUID
        val md = MessageDigest.getInstance("SHA-1")
        val hash = md.digest(combined.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    // --- INNER WEBSOCKET CLIENT HANDLER ---
    private class WebSocketClient(
        private val socket: Socket,
        private val inputStream: InputStream,
        private val outputStream: OutputStream,
        private val onMessage: (String) -> Unit
    ) {
        private val isClosed = AtomicBoolean(false)
        private val writeLock = Any()

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
                            onMessage(text)
                        }
                        0x08 -> { // Close
                            break
                        }
                        0x09 -> { // Ping
                            sendFrame(0x0A, payload) // Pong
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
            sendFrame(0x02, data)
        }

        fun sendText(text: String) {
            sendFrame(0x01, text.toByteArray(Charsets.UTF_8))
        }

        private fun sendFrame(opcode: Int, payload: ByteArray) {
            if (isClosed.get()) return
            try {
                synchronized(writeLock) {
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
            } catch (e: Exception) {
                close()
            }
        }

        fun close() {
            if (isClosed.getAndSet(true)) return
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }
}
