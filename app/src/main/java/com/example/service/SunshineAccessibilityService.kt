package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean

class SunshineAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SunshineAccessibility"
        var instance: SunshineAccessibilityService? = null
            private set

        val isServiceActive: Boolean
            get() = instance != null
    }

    private val isInjecting = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMouseX = -1f
    private var lastMouseY = -1f
    private var isMouseDown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "SunshineAccessibilityService connected. Remote touch injection is ACTIVE!")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op - we only use this service for gesture injection
    }

    override fun onInterrupt() {
        Log.w(TAG, "SunshineAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "SunshineAccessibilityService destroyed")
    }

    /**
     * Injects a tap gesture at normalized coordinates (0.0 - 1.0).
     */
    fun injectTap(normX: Float, normY: Float) {
        val dm = resources.displayMetrics
        val pxX = (normX.coerceIn(0f, 1f) * dm.widthPixels)
        val pxY = (normY.coerceIn(0f, 1f) * dm.heightPixels)

        val path = Path().apply {
            moveTo(pxX, pxY)
            lineTo(pxX, pxY)
        }

        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                Log.d(TAG, "Tap injected successfully at (${pxX.toInt()}, ${pxY.toInt()})")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                Log.w(TAG, "Tap gesture cancelled at (${pxX.toInt()}, ${pxY.toInt()})")
            }
        }, mainHandler)
    }

    /**
     * Handles continuous mouse clicks, moves, and releases.
     */
    fun injectMouse(normX: Float, normY: Float, action: String) {
        val dm = resources.displayMetrics
        val pxX = (normX.coerceIn(0f, 1f) * dm.widthPixels)
        val pxY = (normY.coerceIn(0f, 1f) * dm.heightPixels)

        when (action) {
            "down" -> {
                isMouseDown = true
                lastMouseX = pxX
                lastMouseY = pxY
                // Send immediate tap on mouse down for instant responsiveness
                injectTap(normX, normY)
            }
            "move" -> {
                if (isMouseDown && lastMouseX >= 0 && lastMouseY >= 0) {
                    val path = Path().apply {
                        moveTo(lastMouseX, lastMouseY)
                        lineTo(pxX, pxY)
                    }
                    val stroke = GestureDescription.StrokeDescription(path, 0, 60)
                    val gesture = GestureDescription.Builder().addStroke(stroke).build()
                    dispatchGesture(gesture, null, mainHandler)
                    lastMouseX = pxX
                    lastMouseY = pxY
                }
            }
            "up" -> {
                isMouseDown = false
                lastMouseX = -1f
                lastMouseY = -1f
            }
        }
    }

    /**
     * Injects game button taps or system actions based on laptop keyboard key.
     */
    fun injectKey(code: String, key: String, isDown: Boolean) {
        if (!isDown) return

        when (code) {
            "Escape", "Backspace" -> {
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
            "KeyH", "Home" -> {
                performGlobalAction(GLOBAL_ACTION_HOME)
            }
            // Emulators / Games standard on-screen button mappings (landscape coordinates)
            // D-Pad Left/Up/Right/Down
            "KeyW", "ArrowUp" -> injectTap(0.18f, 0.65f)
            "KeyS", "ArrowDown" -> injectTap(0.18f, 0.85f)
            "KeyA", "ArrowLeft" -> injectTap(0.10f, 0.75f)
            "KeyD", "ArrowRight" -> injectTap(0.26f, 0.75f)

            // Action buttons (Cross, Circle, Square, Triangle / A, B, X, Y)
            "KeyJ" -> injectTap(0.85f, 0.85f) // Cross / A
            "KeyK" -> injectTap(0.92f, 0.75f) // Circle / B
            "KeyU" -> injectTap(0.78f, 0.75f) // Square / X
            "KeyI" -> injectTap(0.85f, 0.65f) // Triangle / Y

            // Shoulder buttons
            "KeyQ" -> injectTap(0.12f, 0.15f) // L1
            "KeyE" -> injectTap(0.88f, 0.15f) // R1

            // Start & Select
            "Enter" -> injectTap(0.60f, 0.90f) // Start
            "Space" -> injectTap(0.40f, 0.90f) // Select
            else -> {
                Log.d(TAG, "Unhandled key: $code ($key)")
            }
        }
    }
}
