package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
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

        var mediaProjection: MediaProjection? = null

        private val _serverStatus = MutableStateFlow(ServerStatus())
        val serverStatus: StateFlow<ServerStatus> = _serverStatus.asStateFlow()

        var currentConfig: StreamConfig = StreamConfig()
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
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
            cachedApps = appRepo.getInstalledGamesAndApps()
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

        startForegroundServiceNotification()
        startStreaming()

        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
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
    }

    private fun startStreaming() {
        val projection = mediaProjection
        if (projection == null) {
            Log.e(TAG, "MediaProjection is null, cannot start screen cast")
            stopSelf()
            return
        }

        val ip = getLocalIpAddress()
        _serverStatus.value = _serverStatus.value.copy(
            isRunning = true,
            isStreaming = true,
            ipAddress = ip,
            port = currentConfig.port
        )

        // 1. Initialize and start HTTP & WebSocket Web Server
        webServer = HttpWebServer(
            port = currentConfig.port,
            getAppsProvider = { cachedApps },
            getStatusProvider = { _serverStatus.value },
            onInputReceived = { event -> handleRemoteInput(event) },
            onKeyFrameRequested = { videoEncoder?.requestKeyFrame() }
        ).apply {
            start(serviceScope)
        }

        // 2. Initialize and start Hardware H.264 Video Encoder
        videoEncoder = HardwareVideoEncoder(
            config = currentConfig,
            onFrameEncoded = { data, isKeyFrame, ptsUs ->
                webServer?.broadcastVideoFrame(data, isKeyFrame, ptsUs)
            },
            onSpsPpsAvailable = { _, _ -> }
        ).apply {
            start(projection, serviceScope)
        }

        // 3. Initialize and start internal Audio Streamer
        if (currentConfig.audioEnabled) {
            audioStreamer = AudioStreamer { data, ptsUs ->
                webServer?.broadcastAudioChunk(data, ptsUs)
            }.apply {
                start(projection, serviceScope)
            }
        }

        Log.i(TAG, "Sunshine Cast streaming started successfully at http://$ip:${currentConfig.port}")
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
                }
            }
        }
    }

    private fun stopStreaming() {
        Log.i(TAG, "Stopping streaming components")
        videoEncoder?.stop()
        videoEncoder = null

        audioStreamer?.stop()
        audioStreamer = null

        webServer?.stop()
        webServer = null

        mediaProjection?.stop()
        mediaProjection = null

        _serverStatus.value = _serverStatus.value.copy(
            isRunning = false,
            isStreaming = false,
            clientCount = 0
        )
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val intf = interfaces.nextElement()
                val addrs = intf.inetAddresses
                while (addrs.hasMoreElements()) {
                    val addr = addrs.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
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
