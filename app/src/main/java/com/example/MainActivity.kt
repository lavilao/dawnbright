package com.example

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import com.example.service.OverlayInputService
import com.example.service.ScreenCastService
import com.example.ui.DashboardScreen
import com.example.ui.DashboardViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                // Media Projection Launcher
                val mediaProjectionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        launchServiceWithResult(result.resultCode, result.data!!)
                    } else {
                        Toast.makeText(this, "Screen capture permission was not granted", Toast.LENGTH_SHORT).show()
                    }
                }

                fun triggerMediaProjection() {
                    try {
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not start screen capture: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }

                // Audio & Notification Permission Launcher (Sequential to avoid concurrent dialog crash)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) {
                    // Proceed to MediaProjection prompt after permission response
                    triggerMediaProjection()
                }

                DashboardScreen(
                    viewModel = viewModel,
                    onStartScreenCast = {
                        val needed = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            needed.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                needed.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        if (needed.isNotEmpty()) {
                            // Request runtime permissions first; callback launches media projection
                            permissionLauncher.launch(needed.toTypedArray())
                        } else {
                            // Permissions already granted, launch media projection directly
                            triggerMediaProjection()
                        }
                    }
                )
            }
        }
    }

    private fun launchServiceWithResult(resultCode: Int, data: Intent) {
        val serviceIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_START
            putExtra(ScreenCastService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCastService.EXTRA_RESULT_DATA, data)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }

            // Start overlay service if permission is available
            if (Settings.canDrawOverlays(this)) {
                val overlayIntent = Intent(this, OverlayInputService::class.java)
                startService(overlayIntent)
            }

            Toast.makeText(this, "Sunshine Cast is live! Connect from Chrome on your laptop.", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to start service: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
