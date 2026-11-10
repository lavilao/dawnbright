package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.atomic.AtomicBoolean

class SunshineAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SunshineAccessibility"
        @Volatile
        var instance: SunshineAccessibilityService? = null
            private set

        val isServiceActive: Boolean
            get() = instance != null
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastMouseX = -1f
    private var lastMouseY = -1f
    private var isMouseDown = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            info.notificationTimeout = 50
            serviceInfo = info
            Log.i(TAG, "SunshineAccessibilityService connected successfully. Remote touch injection is ACTIVE!")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onServiceConnected: ${e.message}", e)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Minimal handling to avoid event starvation
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
        try {
            val dm = resources.displayMetrics
            val pxX = (normX.coerceIn(0f, 1f) * dm.widthPixels)
            val pxY = (normY.coerceIn(0f, 1f) * dm.heightPixels)

            val path = Path().apply {
                moveTo(pxX, pxY)
                // Offset by 1 pixel so the Path has a distinct end point for strict OEM parsers
                lineTo(pxX + 1f, pxY + 1f)
            }

            val stroke = GestureDescription.StrokeDescription(path, 0, 45)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()

            dispatchGesture(gesture, null, mainHandler)
            Log.d(TAG, "Tap injected at (${pxX.toInt()}, ${pxY.toInt()})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to inject tap: ${e.message}")
        }
    }

    /**
     * Handles continuous mouse clicks, moves, and releases.
     */
    fun injectMouse(normX: Float, normY: Float, action: String) {
        try {
            val dm = resources.displayMetrics
            val pxX = (normX.coerceIn(0f, 1f) * dm.widthPixels)
            val pxY = (normY.coerceIn(0f, 1f) * dm.heightPixels)

            when (action) {
                "down" -> {
                    isMouseDown = true
                    lastMouseX = pxX
                    lastMouseY = pxY
                    injectTap(normX, normY)
                }
                "move" -> {
                    if (isMouseDown && lastMouseX >= 0 && lastMouseY >= 0) {
                        val dx = Math.abs(pxX - lastMouseX)
                        val dy = Math.abs(pxY - lastMouseY)
                        // Only dispatch gesture if there's real movement (> 4px)
                        if (dx > 4f || dy > 4f) {
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
                }
                "up" -> {
                    isMouseDown = false
                    lastMouseX = -1f
                    lastMouseY = -1f
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in injectMouse: ${e.message}")
        }
    }

    /**
     * Full Keyboard Input Injection (KDE Connect style):
     * 1. If an editable text field is focused on screen, injects text directly via AccessibilityNodeInfo (ACTION_SET_TEXT).
     * 2. If in a game or emulator, maps keys to touch buttons or virtual game controller.
     * 3. Handles system navigation (Back, Home).
     */
    fun injectKey(code: String, key: String, isDown: Boolean) {
        if (!isDown) return

        // 1. Global Navigation Keys
        if (code == "Escape") {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        if (code == "Home") {
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        // 2. Check if a text input field is focused (for full laptop typing on phone)
        try {
            val root = rootInActiveWindow
            val focusedNode = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode != null && focusedNode.isEditable) {
                val currentText = focusedNode.text?.toString() ?: ""
                if (code == "Backspace") {
                    if (currentText.isNotEmpty()) {
                        val newText = currentText.substring(0, currentText.length - 1)
                        val bundle = Bundle().apply {
                            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                        }
                        focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    }
                    return
                } else if (key.length == 1 && !key.first().isISOControl()) {
                    val newText = currentText + key
                    val bundle = Bundle().apply {
                        putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                    }
                    focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
                    return
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Text field focus check skipped: ${e.message}")
        }

        // 3. Game & Emulator Control Mapping (Landscape coordinates)
        when (code) {
            // D-Pad / Movement
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
