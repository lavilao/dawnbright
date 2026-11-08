package com.example.data.model

data class StreamConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 60,
    val bitrateMbps: Int = 12,
    val audioEnabled: Boolean = true,
    val port: Int = 8080,
    val lowLatencyMode: Boolean = true
) {
    val bitrateBps: Int
        get() = bitrateMbps * 1_000_000

    fun resolutionLabel(): String = "${width}x${height} @ ${fps}fps"
}
