package com.example.service

import android.inputmethodservice.InputMethodService
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup

class SunshineKeyboardService : InputMethodService() {

    companion object {
        private const val TAG = "SunshineKeyboard"
        var instance: SunshineKeyboardService? = null
            private set

        val isServiceActive: Boolean
            get() = instance != null

        fun sendRemoteKey(code: String, key: String, isDown: Boolean): Boolean {
            val s = instance ?: return false
            return s.handleRemoteKey(code, key, isDown)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "SunshineKeyboardService active (KDE Connect style Remote IME)")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) instance = null
        Log.i(TAG, "SunshineKeyboardService destroyed")
    }

    override fun onCreateInputView(): View {
        return View(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
        }
    }

    fun handleRemoteKey(code: String, key: String, isDown: Boolean): Boolean {
        val ic = currentInputConnection

        if (code == "Backspace") {
            if (isDown) {
                if (ic != null) {
                    ic.deleteSurroundingText(1, 0)
                } else {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
                }
            }
            return true
        }

        if (code == "Enter") {
            if (isDown) {
                if (ic != null) {
                    sendDefaultEditorAction(true)
                }
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
            return true
        }

        if (code == "Tab") {
            if (isDown) sendDownUpKeyEvents(KeyEvent.KEYCODE_TAB)
            return true
        }

        if (code == "Escape") {
            if (isDown) sendDownUpKeyEvents(KeyEvent.KEYCODE_ESCAPE)
            return true
        }

        // Directional & game keys
        val gameKeyCode = when (code) {
            "ArrowUp" -> KeyEvent.KEYCODE_DPAD_UP
            "ArrowDown" -> KeyEvent.KEYCODE_DPAD_DOWN
            "ArrowLeft" -> KeyEvent.KEYCODE_DPAD_LEFT
            "ArrowRight" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "Space" -> KeyEvent.KEYCODE_SPACE
            else -> null
        }

        if (gameKeyCode != null && isDown) {
            sendDownUpKeyEvents(gameKeyCode)
            return true
        }

        // Direct printable character commit (for typing any text on phone)
        if (key.length == 1 && isDown) {
            if (ic != null) {
                ic.commitText(key, 1)
            } else {
                val ch = key.first()
                val metaState = if (ch.isUpperCase()) KeyEvent.META_SHIFT_ON else 0
                val event = KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_UNKNOWN, 0, metaState)
                sendKeyChar(ch)
            }
            return true
        }

        return false
    }
}
