package com.example.data.model

sealed class RemoteInputEvent {
    data class Keyboard(
        val code: String,
        val key: String,
        val isDown: Boolean,
        val modifiers: List<String> = emptyList()
    ) : RemoteInputEvent()

    data class Mouse(
        val type: String, // "move", "down", "up"
        val x: Float,     // Normalized 0.0 .. 1.0
        val y: Float,     // Normalized 0.0 .. 1.0
        val button: Int = 0,
        val movementX: Float = 0f,
        val movementY: Float = 0f
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
