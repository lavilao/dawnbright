package com.example.service

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.AppRepository
import com.example.data.model.GameApp
import com.example.data.model.RemoteInputEvent
import com.example.data.model.ServerStatus
import com.example.data.model.StreamConfig
import com.example.encoder.AudioStreamer
import com.example.encoder.HardwareVideoEncoder
import com.example.server.HttpWebServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

class ScreenCastService : Service() {

    companion object {
        private const val TAG = "ScreenCastService"
        private const val NOTIFICATION_ID = 4040
        private const val CHANNEL_ID = "sunshine_cast_channel"

        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"

        const val EXTRA_RESULT_CODE = "com.example.extra.RESULT_CODE"
        const val EXTRA_RESULT_DATA = "com.example.extra.RESULT_DATA"

        private val _serverStatus = MutableStateFlow(ServerStatus())
        val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

        var currentConfig: StreamConfig = StreamConfig()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var mediaProjection: MediaProjection? = null
    private var videoEncoder: HardwareVideoEncoder? = null
    private var audioStreamer: AudioStreamer? = null
    private var webServer: HttpWebServer? = null
    private lateinit var appRepo: AppRepository
    private var cachedApps: List<GameApp> = emptyList()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        appRepo = AppRepository(this)
        createNotificationChannel()

        serviceScope.launch(Dispatchers.IO) {
            try {
                cachedApps = appRepo.getInstalledGamesAndApps()
            } catch (e: Exception) {
                Log.w(TAG, "Error caching installed apps: ${e.message}")
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopStreaming()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // CRITICAL ON ANDROID 10, 11, 14+:
        // startForeground MUST be called BEFORE initializing or using MediaProjection
        startForegroundServiceNotification()

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        if (resultCode == Activity.RESULT_OK && resultData != null) {
            startStreaming(resultCode, resultData)
        } else {
            Log.w(TAG, "Invalid resultCode ($resultCode) or resultData ($resultData)")
        }

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        try {
            val launchIntent = Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                this, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val ip = getLocalIpAddress()
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Sunshine Cast Server")
                .setContentText("Remote Play active at http://$ip:${currentConfig.port}")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground notification", e)
        }
    }

    private fun startStreaming(resultCode: Int, resultData: Intent) {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = projectionManager.getMediaProjection(resultCode, resultData)

            if (projection == null) {
                Log.e(TAG, "MediaProjection is null from MediaProjectionManager")
                return
            }

            // Register callback required on Android 14+
            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.i(TAG, "MediaProjection stopped by system")
                    stopStreaming()
                }
            }, null)

            this.mediaProjection = projection

            val ip = getLocalIpAddress()
            _serverStatus.value = _serverStatus.value.copy(
                isRunning = true,
                isStreaming = true,
                ipAddress = ip,
                port = currentConfig.port
            )

            // 1. Initialize and start HTTP & WebSocket Web Server
            try {
                webServer = HttpWebServer(
                    port = currentConfig.port,
                    getAppsProvider = { cachedApps },
                    getStatusProvider = { _serverStatus.value },
                    onInputReceived = { event -> handleRemoteInput(event) },
                    onKeyFrameRequested = { videoEncoder?.requestKeyFrame() }
                ).apply {
                    start(serviceScope)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting HttpWebServer: ${e.message}", e)
            }

            // 2. Initialize and start Hardware H.264 Video Encoder
            try {
                videoEncoder = HardwareVideoEncoder(
                    config = currentConfig,
                    onFrameEncoded = { data, isKeyFrame, ptsUs ->
                        webServer?.broadcastVideoFrame(data, isKeyFrame, ptsUs)
                    },
                    onSpsPpsAvailable = { _, _ -> }
                ).apply {
                    start(projection, serviceScope)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting HardwareVideoEncoder: ${e.message}", e)
            }

            // 3. Initialize and start internal Audio Streamer (with safe isolation)
            if (currentConfig.audioEnabled) {
                try {
                    audioStreamer = AudioStreamer { data, ptsUs ->
                        webServer?.broadcastAudioChunk(data, ptsUs)
                    }.apply {
                        start(projection, serviceScope)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioStreamer not supported on this device/configuration: ${e.message}")
                }
            }

            Log.i(TAG, "Sunshine Cast streaming started successfully at http://$ip:${currentConfig.port}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start streaming pipeline", e)
            stopStreaming()
        }
    }

    private fun handleRemoteInput(event: RemoteInputEvent) {
        when (event) {
            is RemoteInputEvent.LaunchApp -> {
                Log.i(TAG, "Remote launch requested for: ${event.packageName}")
                val success = appRepo.launchPackage(event.packageName)
                if (success) {
                    val launchedApp = cachedApps.find { it.packageName == event.packageName }
                    val name = launchedApp?.appName ?: event.packageName
                    _serverStatus.value = _serverStatus.value.copy(
                        activeAppName = name,
                        activeAppPackage = event.packageName
                    )
                    webServer?.broadcastText("""{"type":"status","activeApp":"$name"}""")
                }
            }
            is RemoteInputEvent.Keyboard -> {
                val stateText = if (event.isDown) "▼ ${event.code}" else "▲ ${event.code}"
                OverlayInputService.updateKeyDisplay(stateText)
            }
            is RemoteInputEvent.Mouse -> {
                OverlayInputService.showTouchRipple(event.x, event.y)
            }
            is RemoteInputEvent.Touch -> {
                OverlayInputService.showTouchRipple(event.x, event.y)
            }
            is RemoteInputEvent.StreamControl -> {
                if (event.action == "request_keyframe") {
                    videoEncoder?.requestKeyFrame()
                } else if (event.action == "set_resolution") {
                    // Dynamic resolution adjustment requested by connected client
                    val parts = event.value.split("x")
                    if (parts.size == 2) {
                        val w = parts[0].toIntOrNull()
                        val h = parts[1].toIntOrNull()
                        if (w != null && h != null && w > 0 && h > 0) {
                            Log.i(TAG, "Client requested custom resolution: ${w}x${h}")
                            currentConfig = currentConfig.copy(width = w, height = h)
                        }
                    }
                }
            }
        }
    }

    private fun stopStreaming() {
        Log.i(TAG, "Stopping streaming components")
        try { videoEncoder?.stop() } catch (e: Exception) { Log.w(TAG, e) }
        videoEncoder = null

        try { audioStreamer?.stop() } catch (e: Exception) { Log.w(TAG, e) }
        audioStreamer = null

        try { webServer?.stop() } catch (e: Exception) { Log.w(TAG, e) }
        webServer = null

        try { mediaProjection?.stop() } catch (e: Exception) { Log.w(TAG, e) }
        mediaProjection = null

        _serverStatus.value = _serverStatus.value.copy(
            isRunning = false,
            isStreaming = false,
            clientCount = 0
        )
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses ?: continue
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val host = addr.hostAddress
                        if (!host.isNullOrEmpty()) {
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting local IP: ${e.message}")
        }
        return "127.0.0.1"
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sunshine Cast Streaming",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground screen mirroring and remote play service"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        serviceScope.cancel()
    }
}
