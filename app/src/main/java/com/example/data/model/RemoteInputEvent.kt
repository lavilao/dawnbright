package com.example.data.model

sealed class RemoteInputEvent {
    data class Keyboard(
        val code: String,
        val key: String,
        val isDown: Boolean,
        val modifiers: List<String> = emptyList(),
        // Optional calibrated tap target (normalized 0..1). When present the key is
        // injected as a press-and-hold touch at this position instead of text/system key.
        val x: Float = -1f,
        val y: Float = -1f
    ) : RemoteInputEvent() {
        val hasTarget: Boolean get() = x >= 0f && y >= 0f
    }

    data class Mouse(
        val type: String, // "move", "down", "up", "wheel"
        val x: Float,     // Normalized 0.0 .. 1.0
        val y: Float,     // Normalized 0.0 .. 1.0
        val button: Int = 0,
        val movementX: Float = 0f,
        val movementY: Float = 0f,
        val deltaY: Float = 0f // For "wheel": positive scrolls down
    ) : RemoteInputEvent()

    data class Touch(
        val type: String, // "down", "move", "up"
        val x: Float,
        val y: Float,
        val pointerId: Int = 0
    ) : RemoteInputEvent()

    data class LaunchApp(
        val packageName: String
    ) : RemoteInputEvent()

    data class StreamControl(
        val action: String, // "request_keyframe", "set_bitrate", "ping"
        val value: String = ""
    ) : RemoteInputEvent()
}
