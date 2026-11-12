package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Path
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Remote input injection service.
 *
 * Injects taps, drags, multi-touch and press-and-hold gestures using the
 * AccessibilityService gesture API, plus system navigation actions and
 * text-typing on focused text fields.
 *
 * Gesture engine notes (per AOSP GestureDescription contract):
 * - Each dispatchGesture() call REPLACES the gesture that is currently running,
 *   so every active pointer must be re-sent in every dispatch as a continuation
 *   stroke (StrokeDescription.continueStroke with willContinue=true).
 * - A stroke marked willContinue keeps its finger logically "down" until a
 *   continuation with willContinue=false closes it.
 * - Paths must be non-empty, with non-negative bounds; a continued stroke's path
 *   must start where the previous segment's path ended.
 * - Max 20 strokes per gesture, max 60s duration per gesture (we stay far below).
 */
class SunshineAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SunshineAccessibility"

        // Timing of the synthetic stroke segments (ms)
        private const val SEGMENT_MS = 300L       // initial / idle runway while finger is down
        private const val MOVE_MIN_MS = 16L        // shortest interpolated move segment
        private const val FINAL_MS = 60L          // closing segment length (tap feel)
        private const val HEARTBEAT_MS = 100L     // how often we extend idle pointers
        private const val IDLE_EXTEND_AFTER_MS = 120L // extend when no event for this long
        private const val HOLD_MAX_MS = 55000L    // safety cap for a stuck held pointer

        @Volatile
        var instance: SunshineAccessibilityService? = null
            private set

        val isServiceActive: Boolean
            get() = instance != null

        /**
         * Invoked when the real binding state changes (connected / destroyed) so the
         * cast service can inform connected web clients. This is the only reliable
         * signal - "enabled in settings" does NOT mean the service is bound.
         */
        var onBindingChanged: ((Boolean) -> Unit)? = null

        // Continuing strokes (continueStroke / willContinue) need API 26+
        private val CONTINUATION_SUPPORTED = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

        // Pointer namespace ids
        private const val MOUSE_ID = "mouse"
        private const val TOUCH_ID = "touch:"
        private const val KEY_ID = "key:"

        // Default on-screen game HUD mapping (normalized 0..1, landscape). Users can
        // calibrate per-game from the browser (stored client-side and sent with the key).
        val defaultKeyMap: Map<String, Pair<Float, Float>> = mapOf(
            "KeyW" to Pair(0.18f, 0.65f), "ArrowUp" to Pair(0.18f, 0.65f),
            "KeyS" to Pair(0.18f, 0.85f), "ArrowDown" to Pair(0.18f, 0.85f),
            "KeyA" to Pair(0.10f, 0.75f), "ArrowLeft" to Pair(0.10f, 0.75f),
            "KeyD" to Pair(0.26f, 0.75f), "ArrowRight" to Pair(0.26f, 0.75f),
            "KeyJ" to Pair(0.85f, 0.85f), // Cross / A
            "KeyK" to Pair(0.92f, 0.75f), // Circle / B
            "KeyU" to Pair(0.78f, 0.75f), // Square / X
            "KeyI" to Pair(0.85f, 0.65f), // Triangle / Y
            "KeyQ" to Pair(0.12f, 0.15f), // L1
            "KeyE" to Pair(0.88f, 0.15f), // R1
            "Enter" to Pair(0.60f, 0.90f), // Start
            "Space" to Pair(0.40f, 0.90f)  // Select
        )
    }

    // ------------------------------------------------------------------
    // Gesture engine
    // ------------------------------------------------------------------

    private class Pointer(
        var stroke: GestureDescription.StrokeDescription,
        var x: Float,           // path end x (px)
        var y: Float,           // path end y (px)
        var lastDispatchAt: Long,
        val beginAt: Long
    )

    private val pointers = HashMap<String, Pointer>()
    private val engineLock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var heartbeat: Runnable? = null

    /** Current display size in px, reflecting rotation (unlike resources.displayMetrics). */
    private fun displaySize(): Point {
        val point = Point()
        try {
            val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            @Suppress("DEPRECATION") // getRealSize covers system bars, which is what we need
            wm.defaultDisplay.getRealSize(point)
            if (point.x > 0 && point.y > 0) return point
        } catch (_: Exception) {
        }
        val dm = resources.displayMetrics
        point.set(dm.widthPixels, dm.heightPixels)
        return point
    }

    private fun toPx(norm: Float, size: Int): Float = norm.coerceIn(0f, 1f) * size

    /** Press a pointer down and start keeping it alive. */
    private fun beginPointer(id: String, pxX: Float, pxY: Float) {
        synchronized(engineLock) {
            if (pointers.containsKey(id)) return // ignore repeats / stuck keydowns
            if (!CONTINUATION_SUPPORTED) {
                // Android 7.x: no continuing-stroke API - degrade to a plain tap
                oneShotTap(pxX, pxY)
                return
            }
            val path = Path().apply {
                moveTo(pxX, pxY)
                lineTo(pxX + 1f, pxY + 1f)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, SEGMENT_MS, true)
            val now = SystemClock.elapsedRealtime()
            pointers[id] = Pointer(stroke, pxX + 1f, pxY + 1f, now, now)
            dispatchLocked(null)
            ensureHeartbeatLocked()
            Log.d(TAG, "Pointer down: " + id + " @ (" + pxX.toInt() + "," + pxY.toInt() + ")")
        }
    }

    /** Move a pressed pointer to a new position. */
    private fun movePointer(id: String, pxX: Float, pxY: Float) {
        synchronized(engineLock) {
            val p = pointers[id] ?: return
            val now = SystemClock.elapsedRealtime()
            val dur = (now - p.lastDispatchAt).coerceIn(MOVE_MIN_MS, SEGMENT_MS)
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(pxX, pxY)
            }
            p.stroke = p.stroke.continueStroke(path, 0L, dur, true)
            p.x = pxX
            p.y = pxY
            p.lastDispatchAt = now
            dispatchLocked(null)
        }
    }

    /** Lift a pointer up. */
    private fun endPointer(id: String) {
        synchronized(engineLock) {
            val p = pointers.remove(id) ?: return
            val path = Path().apply {
                moveTo(p.x, p.y)
                lineTo(p.x + 1f, p.y + 1f)
            }
            val final = p.stroke.continueStroke(path, 0L, FINAL_MS, false)
            dispatchLocked(final)
            Log.d(TAG, "Pointer up: " + id)
        }
    }

    /**
     * Dispatch one combined gesture: a continuation stroke for every active
     * pointer, plus an optional closing stroke for a pointer that just ended.
     */
    private fun dispatchLocked(extraFinal: GestureDescription.StrokeDescription?) {
        if (pointers.isEmpty() && extraFinal == null) return
        try {
            val builder = GestureDescription.Builder()
            for (p in pointers.values) builder.addStroke(p.stroke)
            if (extraFinal != null) builder.addStroke(extraFinal)
            dispatchGesture(builder.build(), null, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "dispatchGesture failed: " + e.message)
        }
    }

    /** Keep pressed-but-idle pointers alive by extending them before their runway expires. */
    private fun ensureHeartbeatLocked() {
        if (heartbeat != null) return
        val r = object : Runnable {
            override fun run() {
                synchronized(engineLock) {
                    if (pointers.isEmpty()) {
                        heartbeat = null
                        return
                    }
                    val now = SystemClock.elapsedRealtime()
                    // Safety: end pointers stuck for too long (lost keyup etc.)
                    val expired = ArrayList<String>()
                    for ((id, p) in pointers) if (now - p.beginAt > HOLD_MAX_MS) expired.add(id)
                    for (id in expired) endPointer(id)
                    if (pointers.isEmpty()) {
                        heartbeat = null
                        return
                    }
                    for ((_, p) in pointers) {
                        if (now - p.lastDispatchAt > IDLE_EXTEND_AFTER_MS) {
                            val path = Path().apply {
                                moveTo(p.x, p.y)
                                lineTo(p.x + 0.5f, p.y + 0.5f)
                            }
                            p.stroke = p.stroke.continueStroke(path, 0L, SEGMENT_MS, true)
                            p.lastDispatchAt = now
                        }
                    }
                    dispatchLocked(null)
                    if (heartbeat === this) {
                        mainHandler.postDelayed(this, HEARTBEAT_MS)
                    }
                }
            }
        }
        heartbeat = r
        mainHandler.postDelayed(r, HEARTBEAT_MS)
    }

    private fun endAllPointers() {
        synchronized(engineLock) {
            val ids = ArrayList(pointers.keys)
            for (id in ids) endPointer(id)
            heartbeat?.let { mainHandler.removeCallbacks(it) }
            heartbeat = null
        }
    }

    // ------------------------------------------------------------------
    // Public input API (called from the web server thread)
    // ------------------------------------------------------------------

    /** Mouse: press (down), drag (move), release (up) as one continuous stroke. */
    fun injectMouse(normX: Float, normY: Float, action: String) {
        val size = displaySize()
        val pxX = toPx(normX, size.x)
        val pxY = toPx(normY, size.y)
        when (action) {
            "down" -> beginPointer(MOUSE_ID, pxX, pxY)
            "move" -> movePointer(MOUSE_ID, pxX, pxY)
            "up" -> endPointer(MOUSE_ID)
        }
    }

    /** Mouse wheel: inject a short vertical swipe at the current position. */
    fun injectWheel(normX: Float, normY: Float, deltaY: Float) {
        if (deltaY == 0f) return
        val size = displaySize()
        val pxX = toPx(normX, size.x)
        val pxY = toPx(normY, size.y)
        val dist = (deltaY / 3f).coerceIn(-400f, 400f) // browser notches -> px
        try {
            val path = Path().apply {
                moveTo(pxX, pxY)
                lineTo(pxX, py(pxY, dist, size.y))
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, 120L)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "injectWheel failed: " + e.message)
        }
    }

    private fun py(start: Float, delta: Float, size: Int): Float =
        (start + delta).coerceIn(0f, size.toFloat())

    /** Multi-touch: press / move / lift a finger identified by pointerId. */
    fun beginTouch(pointerId: Int, normX: Float, normY: Float) {
        val size = displaySize()
        beginPointer(TOUCH_ID + pointerId, toPx(normX, size.x), toPx(normY, size.y))
    }

    fun moveTouch(pointerId: Int, normX: Float, normY: Float) {
        val size = displaySize()
        movePointer(TOUCH_ID + pointerId, toPx(normX, size.x), toPx(normY, size.y))
    }

    fun endTouch(pointerId: Int) {
        endPointer(TOUCH_ID + pointerId)
    }

    /**
     * A keyboard key mapped to an on-screen position (calibrated or default HUD):
     * keydown presses and HOLDS the position, keyup releases it.
     */
    fun handleKeyButton(code: String, normX: Float, normY: Float, isDown: Boolean) {
        val size = displaySize()
        if (isDown) {
            beginPointer(KEY_ID + code, toPx(normX, size.x), toPx(normY, size.y))
        } else {
            endPointer(KEY_ID + code)
        }
    }

    /**
     * Un-positioned keyboard keys: system navigation, text typing on focused
     * fields (when the IME is not the active keyboard), then the default game
     * HUD mapping (which itself supports hold via handleKeyButton).
     */
    fun injectKey(code: String, key: String, isDown: Boolean) {
        if (isDown) {
            if (code == "Escape") {
                performGlobalAction(GLOBAL_ACTION_BACK)
                return
            }
            if (code == "Home" || code == "KeyH") {
                performGlobalAction(GLOBAL_ACTION_HOME)
                return
            }
        }
        if (isDown && tryInjectText(code, key)) return
        if (isDown && code == "Backspace") {
            performGlobalAction(GLOBAL_ACTION_BACK)
            return
        }
        val target = defaultKeyMap[code]
        if (target != null) {
            handleKeyButton(code, target.first, target.second, isDown)
            return
        }
        if (isDown) Log.d(TAG, "Unhandled key: " + code + " (" + key + ")")
    }

    /** One-shot tap dispatch (works on API 24+; used on devices without continuation support). */
    private fun oneShotTap(pxX: Float, pxY: Float) {
        try {
            val path = Path().apply {
                moveTo(pxX, pxY)
                lineTo(pxX + 1f, pxY + 1f)
            }
            val stroke = GestureDescription.StrokeDescription(path, 0L, FINAL_MS)
            dispatchGesture(GestureDescription.Builder().addStroke(stroke).build(), null, mainHandler)
        } catch (e: Exception) {
            Log.e(TAG, "oneShotTap failed: " + e.message)
        }
    }

    /** Simple one-shot tap in normalized coordinates. */
    fun injectTap(normX: Float, normY: Float) {
        val size = displaySize()
        oneShotTap(toPx(normX, size.x), toPx(normY, size.y))
    }

    // ------------------------------------------------------------------
    // Text typing on focused fields (IME fallback path)
    // ------------------------------------------------------------------

    private fun tryInjectText(code: String, key: String): Boolean {
        return try {
            val root = rootInActiveWindow ?: return false
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
            if (!focused.isEditable) return false
            val currentText = focused.text?.toString() ?: ""
            if (code == "Backspace") {
                if (currentText.isNotEmpty()) {
                    setNodeText(focused, currentText.substring(0, currentText.length - 1))
                }
                true
            } else if (key.length == 1 && !key.first().isISOControl()) {
                setNodeText(focused, currentText + key)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.d(TAG, "Text field focus check skipped: " + e.message)
            false
        }
    }

    private fun setNodeText(node: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        try {
            // Keep the service quiet: we only inject gestures and read the active
            // window on demand. Listening to everything wastes battery and janks
            // the main thread.
            val info = serviceInfo ?: AccessibilityServiceInfo()
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            info.notificationTimeout = 0
            serviceInfo = info
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service info: " + e.message)
        }
        Log.i(TAG, "SunshineAccessibilityService CONNECTED. Remote input injection is ACTIVE.")
        try {
            onBindingChanged?.invoke(true)
        } catch (_: Exception) {
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used: we poll the active window only when a key needs it.
    }

    override fun onInterrupt() {
        Log.w(TAG, "SunshineAccessibilityService interrupted")
    }

    override fun onDestroy() {
        try {
            endAllPointers()
        } catch (_: Exception) {
        }
        if (instance == this) {
            instance = null
        }
        Log.i(TAG, "SunshineAccessibilityService destroyed")
        try {
            onBindingChanged?.invoke(false)
        } catch (_: Exception) {
        }
        super.onDestroy()
    }
}
