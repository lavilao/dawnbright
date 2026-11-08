package com.example.data.model

data class ServerStatus(
    val isRunning: Boolean = false,
    val isStreaming: Boolean = false,
    val ipAddress: String = "127.0.0.1",
    val port: Int = 8080,
    val clientCount: Int = 0,
    val currentFps: Int = 0,
    val currentBitrateMbps: Float = 0f,
    val activeAppName: String = "",
    val activeAppPackage: String = "",
    val totalBytesSent: Long = 0L,
    val lastPingMs: Long = 0L
) {
    val serverUrl: String
        get() = "http://$ipAddress:$port"
}
