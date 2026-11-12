package com.example.service

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * KDE Connect style remote keyboard IME.
 *
 * Receives laptop keystrokes over WebSocket (via ScreenCastService) and types them
 * into whatever text field is focused on the phone while this IME is the SELECTED
 * keyboard.
 *
 * CRITICAL contract: we only claim a key when we actually have a live
 * [currentInputConnection] (a text field is focused). If we return true without
 * one, keys vanish - that was the bug that swallowed WASD/game keys in v1.0.6.
 * Returning false lets the event fall through to the accessibility layer.
 */
class SunshineKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "SunshineKeyboard"

        @Volatile
        var instance: SunshineKeyboardService? = null
            private set

        val isServiceActive: Boolean
            get() = instance != null

        /**
         * Route a remote key. Returns true ONLY if this IME actually consumed it
         * (typed / deleted / navigated inside a focused text field).
         */
        fun sendRemoteKey(code: String, key: String, isDown: Boolean): Boolean {
            val s = instance ?: return false
            return try {
                s.handleRemoteKey(code, key, isDown)
            } catch (e: Exception) {
                Log.w(TAG, "handleRemoteKey failed: " + e.message)
                false
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "SunshineKeyboardService active (KDE Connect style Remote IME)")
    }

    override fun onDestroy() {
        if (instance == this) instance = null
        Log.i(TAG, "SunshineKeyboardService destroyed")
        super.onDestroy()
    }

    /**
     * Tiny non-intrusive bar shown where a real keyboard would be. Tapping it opens
     * the system keyboard picker so the phone user can switch back to their normal
     * keyboard (prevents "I can't type on my own phone anymore" lockout).
     */
    override fun onCreateInputView(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.CENTER_HORIZONTAL
            setBackgroundColor(0xFF0D1117.toInt())
            val pad = (resources.displayMetrics.density * 8).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val label = TextView(this).apply {
            text = "Sunshine Remote Keyboard active (laptop keys type here)"
            textSize = 13f
            setTextColor(0xFF00E5FF.toInt())
        }

        val switchBtn = Button(this).apply {
            text = "Switch keyboard"
            textSize = 12f
            setOnClickListener {
                try {
                    (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
                        .showInputMethodPicker()
                } catch (e: Exception) {
                    Log.w(TAG, "Could not show input method picker: " + e.message)
                }
            }
        }

        row.addView(label, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { weight = 1f; marginEnd = 12 })
        row.addView(switchBtn)
        return row
    }

    fun handleRemoteKey(code: String, key: String, isDown: Boolean): Boolean {
        if (!isDown) return false // keyups are handled by the accessibility hold engine

        // Only claim keys while a text field is focused and we hold its connection.
        val ic = currentInputConnection ?: return false

        when (code) {
            "Backspace" -> {
                ic.deleteSurroundingText(1, 0)
                return true
            }
            "Enter" -> {
                sendDefaultEditorAction(true)
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                return true
            }
            "Tab" -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
                return true
            }
            "ArrowUp" -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_UP)
                return true
            }
            "ArrowDown" -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_DOWN)
                return true
            }
            "ArrowLeft" -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
                return true
            }
            "ArrowRight" -> {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_RIGHT)
                return true
            }
        }

        // Printable characters (letters, digits, symbols - including shifted ones,
        // which arrive here as the final upper-case character in `key`).
        if (key.length == 1 && !key.first().isISOControl()) {
            ic.commitText(key, 1)
            return true
        }

        return false // anything else -> let the accessibility layer decide
    }
}
