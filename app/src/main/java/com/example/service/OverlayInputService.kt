package com.example.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.example.MainActivity

class OverlayInputService : Service() {

    companion object {
        private var instance: OverlayInputService? = null

        fun updateKeyDisplay(keySummary: String) {
            instance?.updateKeys(keySummary)
        }

        fun showTouchRipple(xNorm: Float, yNorm: Float) {
            instance?.displayTouch(xNorm, yNorm)
        }
    }

    private var windowManager: WindowManager? = null
    private var overlayView: FrameLayout? = null
    private var keyTextView: TextView? = null
    private var touchIndicator: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return
        }

        setupOverlay()
    }

    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        val root = FrameLayout(this)

        // Top-left compact badge showing incoming remote inputs
        val badgeLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#E6090D16"))
            setPadding(24, 12, 24, 12)
            elevation = 12f
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins(40, 60, 0, 0)
            }
        }

        val dot = View(this).apply {
            setBackgroundColor(Color.parseColor("#00E676"))
            layoutParams = LinearLayout.LayoutParams(16, 16).apply {
                gravity = Gravity.CENTER_VERTICAL
                setMargins(0, 0, 16, 0)
            }
        }

        keyTextView = TextView(this).apply {
            text = "Sunshine: Live"
            setTextColor(Color.parseColor("#00E5FF"))
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
        }

        badgeLayout.addView(dot)
        badgeLayout.addView(keyTextView)
        root.addView(badgeLayout)

        // Touch position visualizer circle
        touchIndicator = View(this).apply {
            setBackgroundColor(Color.parseColor("#8000E5FF"))
            layoutParams = FrameLayout.LayoutParams(48, 48).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            visibility = View.GONE
        }
        root.addView(touchIndicator)

        overlayView = root
        try {
            windowManager?.addView(root, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateKeys(text: String) {
        keyTextView?.post {
            keyTextView?.text = if (text.isEmpty()) "Sunshine: Live" else "Key: $text"
        }
    }

    private fun displayTouch(xNorm: Float, yNorm: Float) {
        touchIndicator?.post {
            val dm = resources.displayMetrics
            val px = (xNorm * dm.widthPixels).toInt() - 24
            val py = (yNorm * dm.heightPixels).toInt() - 24

            val lp = touchIndicator?.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.leftMargin = px
                lp.topMargin = py
                touchIndicator?.layoutParams = lp
                touchIndicator?.visibility = View.VISIBLE

                touchIndicator?.removeCallbacks(hideTouchRunnable)
                touchIndicator?.postDelayed(hideTouchRunnable, 300)
            }
        }
    }

    private val hideTouchRunnable = Runnable {
        touchIndicator?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        if (overlayView != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (_: Exception) {}
            overlayView = null
        }
    }
}
