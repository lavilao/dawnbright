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
                val mediaProjectionManager = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                        startScreenCastService(result.resultCode, result.data!!)
                    } else {
                        Toast.makeText(this, "Screen capture permission denied", Toast.LENGTH_SHORT).show()
                    }
                }

                // Permission Launcher (Audio + Notifications)
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    // Audio and notification status checked
                }

                DashboardScreen(
                    viewModel = viewModel,
                    onStartScreenCast = {
                        // Request necessary runtime permissions
                        val permissionsToRequest = mutableListOf<String>()
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }

                        if (permissionsToRequest.isNotEmpty()) {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        }

                        // Launch MediaProjection consent dialog
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionManager.launch(projectionManager.createScreenCaptureIntent())
                    }
                )
            }
        }
    }

    private fun startScreenCastService(resultCode: Int, data: Intent) {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = projectionManager.getMediaProjection(resultCode, data)

        if (projection == null) {
            Toast.makeText(this, "Failed to initialize MediaProjection", Toast.LENGTH_SHORT).show()
            return
        }

        ScreenCastService.mediaProjection = projection

        val serviceIntent = Intent(this, ScreenCastService::class.java).apply {
            action = ScreenCastService.ACTION_START
        }

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
    }
}
