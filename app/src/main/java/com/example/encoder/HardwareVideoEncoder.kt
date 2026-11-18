package com.example.encoder

import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import com.example.data.model.StreamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Hardware H.264 encoder bound to a MediaProjection VirtualDisplay.
 *
 * The encoded frame is aspect-matched to the physical screen so AUTO_MIRROR
 * never letterboxes content into the frame. Because games rotate the phone
 * mid-session, the encoder watches the display orientation and rebuilds the
 * codec + VirtualDisplay when it flips: a stream started on the portrait
 * dashboard switches to a landscape frame as soon as a landscape game opens,
 * and back when it closes.
 */
class HardwareVideoEncoder(
    private val config: StreamConfig,
    private val onFrameEncoded: (data: ByteArray, isKeyFrame: Boolean, timestampUs: Long) -> Unit,
    private val onSpsPpsAvailable: (sps: ByteArray, pps: ByteArray) -> Unit,
    /** Fired whenever the encoded frame size changes (initial start and every rotation rebuild). */
    private val onFrameSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> },
    /** Reports encoder setup/output failures so the service cannot report a healthy stream. */
    private val onEncoderError: (message: String) -> Unit = {}
) {
    companion object {
        private const val TAG = "HardwareVideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val TIMEOUT_US = 10_000L

        /** Cooldown after a rotation before rebuilding (rotation events fire in bursts). */
        private const val ROTATION_SETTLE_MS = 400L
        private const val NO_FRAME_TIMEOUT_MS = 8_000L

        @Volatile
        var latestSyncFrame: ByteArray? = null
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encodingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    /** Effective H.264 picture size actually configured (aspect-matched to the screen). */
    @Volatile
    var frameWidth: Int = 0
        private set

    @Volatile
    var frameHeight: Int = 0
        private set

    private var cachedConfigHeader: ByteArray? = null

    private var mediaProjection: MediaProjection? = null
    private var scope: CoroutineScope? = null
    private var getDisplaySize: (() -> Point)? = null

    /** Serializes codec lifecycle ops (drain loop vs. rotation rebuild). */
    private val codecLock = Any()

    /** Bumped on every rebuild so stale drain loops exit instead of touching a dead codec. */
    private val generation = AtomicInteger(0)
    private val noFrameErrorReported = AtomicBoolean(false)
    @Volatile private var lastVideoFrameAtMs: Long = 0L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val rebuildExecutor = Executors.newSingleThreadExecutor()
    private val rotationCheck = Runnable {
        try {
            rebuildExecutor.execute { rebuildForRotation() }
        } catch (_: Exception) {
            // executor already shut down (stop() raced the pending check)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * @param getDisplaySize rotation-aware current display size provider; used
     *        for the initial frame size and re-queried after every rotation.
     */
    fun start(mediaProjection: MediaProjection, scope: CoroutineScope, getDisplaySize: (() -> Point)? = null) {
        synchronized(codecLock) {
            if (isRunning.get()) return
            this.mediaProjection = mediaProjection
            this.scope = scope
            this.getDisplaySize = getDisplaySize

            val display = try { getDisplaySize?.invoke() } catch (_: Exception) { null }
            val size = computeFrameSize(display ?: Point(config.width, config.height))
            if (!startPipelineLocked(size.first, size.second, generation.get())) {
                isRunning.set(false)
                return
            }

            frameWidth = size.first
            frameHeight = size.second
            onFrameSizeChanged(size.first, size.second)
        }
    }

    /**
     * Called by the cast service whenever the default display reports a change
     * (rotation, size). Debounced, then checked: only an orientation flip
     * triggers a rebuild - same-orientation size jitter (navbar, keyboard) is
     * absorbed by the letterbox-aware input mapping instead.
     */
    fun notifyDisplayChanged() {
        if (!isRunning.get()) return
        mainHandler.removeCallbacks(rotationCheck)
        mainHandler.postDelayed(rotationCheck, ROTATION_SETTLE_MS)
    }

    fun requestKeyFrame() {
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            synchronized(codecLock) { mediaCodec?.setParameters(params) }
            Log.d(TAG, "Keyframe (IDR) requested from hardware encoder")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request sync frame: ${e.message}")
        }
    }

    fun stop() {
        val wasRunning = isRunning.getAndSet(false)
        if (wasRunning) Log.i(TAG, "Stopping hardware video encoder")

        // A failed initial start or rotation rebuild may already have cleared
        // isRunning. Cleanup must still run so the executor and any partially
        // initialized resources do not survive service shutdown.
        mainHandler.removeCallbacksAndMessages(null)
        try { rebuildExecutor.shutdownNow() } catch (_: Exception) {}

        encodingJob?.cancel()
        encodingJob = null

        synchronized(codecLock) {
            try { virtualDisplay?.release() } catch (e: Exception) {
                Log.w(TAG, "Error releasing VirtualDisplay: ${e.message}")
            }
            virtualDisplay = null

            try { inputSurface?.release() } catch (e: Exception) {
                Log.w(TAG, "Error releasing InputSurface: ${e.message}")
            }
            inputSurface = null

            try { mediaCodec?.stop() } catch (e: Exception) { Log.w(TAG, "mediaCodec.stop: ${e.message}") }
            try { mediaCodec?.release() } catch (e: Exception) { Log.w(TAG, "mediaCodec.release: ${e.message}") }
            mediaCodec = null
            cachedConfigHeader = null
        }
    }

    // ------------------------------------------------------------------
    // Rotation handling
    // ------------------------------------------------------------------

    private fun rebuildForRotation() {
        val size = try { getDisplaySize?.invoke() } catch (_: Exception) { null } ?: return
        if (!isRunning.get()) return

        var newW = 0
        var newH = 0
        synchronized(codecLock) {
            if (!isRunning.get()) return
            val displayLandscape = size.x > size.y
            val frameLandscape = frameWidth > frameHeight
            if (displayLandscape == frameLandscape) return // not an orientation flip

            val gen = generation.incrementAndGet()
            Log.i(TAG, "Display rotated to ${size.x}x${size.y}; rebuilding encoder (was ${frameWidth}x${frameHeight})")

            // Tear down the old pipeline; the old drain loop exits via the generation check.
            encodingJob?.cancel()
            encodingJob = null
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { mediaCodec?.stop() } catch (_: Exception) {}
            try { mediaCodec?.release() } catch (_: Exception) {}
            mediaCodec = null
            try { inputSurface?.release() } catch (_: Exception) {}
            inputSurface = null
            cachedConfigHeader = null

            val newSize = computeFrameSize(size)
            if (startPipelineLocked(newSize.first, newSize.second, gen)) {
                frameWidth = newSize.first
                frameHeight = newSize.second
                newW = newSize.first
                newH = newSize.second
                // Old cached keyframe has the wrong geometry - drop it; a fresh
                // IDR (with new SPS/PPS) arrives within a frame.
                latestSyncFrame = null
            } else {
                val message = "H.264 encoder failed to restart after display rotation"
                Log.e(TAG, "$message; stream will need to be restarted")
                onEncoderError(message)
            }
        }
        if (newW > 0 && newH > 0) {
            onFrameSizeChanged(newW, newH)
        }
    }

    // ------------------------------------------------------------------
    // Pipeline construction
    // ------------------------------------------------------------------

    /** Resolution budget from the config, matched to the physical screen aspect. */
    private fun computeFrameSize(display: Point): Pair<Int, Int> {
        val maxW = alignDown(config.width.toFloat(), 16).coerceAtLeast(320)
        val maxH = alignDown(config.height.toFloat(), 16).coerceAtLeast(240)
        if (display.x <= 0 || display.y <= 0) return Pair(maxW, maxH)
        val scale = minOf(
            maxW.toFloat() / display.x,
            maxH.toFloat() / display.y
        )
        // AVC encoders commonly require 16x16 macroblock-aligned dimensions.
        // Even dimensions alone (for example 486x1080) can fail configure().
        val w = alignDown(display.x * scale, 16).coerceAtLeast(320)
        val h = alignDown(display.y * scale, 16).coerceAtLeast(240)
        return Pair(w, h)
    }

    private fun alignDown(value: Float, alignment: Int): Int = value.toInt() / alignment * alignment

    private fun buildVideoFormat(
        encWidth: Int,
        encHeight: Int,
        includeLowLatencyHints: Boolean = true,
        bitrateMode: Int = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
    ): MediaFormat {
        return MediaFormat.createVideoFormat(MIME_TYPE, encWidth, encHeight).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
            setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1s for instant sync

            // Prepend SPS/PPS headers to EVERY sync frame so connected browsers never miss decoder config
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
                } else {
                    setInteger("prepend-sps-pps-to-idr-frames", 1)
                }
            } catch (_: Exception) {}

            try {
                setInteger(MediaFormat.KEY_BITRATE_MODE, bitrateMode)
            } catch (_: Exception) {}

            // Retry without device-specific low-latency keys if the codec rejects them.
            if (includeLowLatencyHints) try {
                    setInteger(MediaFormat.KEY_PRIORITY, 0)
                    setInteger("latency", 0)
                    setInteger(MediaFormat.KEY_OPERATING_RATE, config.fps)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                    }
                } catch (_: Exception) {}
        }
    }

    private fun createAndConfigureCodec(format: MediaFormat): MediaCodec {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            return codec
        } catch (firstError: Exception) {
            try { codec.release() } catch (_: Exception) {}
            Log.w(TAG, "Configure with optimized CBR settings failed, retrying baseline AVC: ${firstError.message}")
        }

        val fallback = MediaCodec.createEncoderByType(MIME_TYPE)
        try {
            fallback.configure(
                buildVideoFormat(
                    width,
                    height,
                    includeLowLatencyHints = false,
                    bitrateMode = MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
                ),
                null,
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE
            )
            Log.i(TAG, "Encoder configured with baseline VBR settings")
            return fallback
        } catch (fallbackError: Exception) {
            try { fallback.release() } catch (_: Exception) {}
            throw IllegalStateException(
                "AVC encoder rejected ${width}x${height} with optimized and baseline settings",
                fallbackError
            )
        }
    }

    /** Creates codec + input surface + VirtualDisplay and starts the drain loop. */
    private fun startPipelineLocked(encWidth: Int, encHeight: Int, gen: Int): Boolean {
        val projection = mediaProjection ?: return false
        return try {
            val format = buildVideoFormat(encWidth, encHeight)
            val codec = createAndConfigureCodec(format)
            val surface = codec.createInputSurface()
            codec.start()

            mediaCodec = codec
            inputSurface = surface

            virtualDisplay = projection.createVirtualDisplay(
                "SunshineCastDisplay",
                encWidth,
                encHeight,
                320, // DPI
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )
            if (virtualDisplay == null) {
                throw IllegalStateException("MediaProjection did not create a virtual display")
            }

            // The display and codec are fully initialized. Publish the running state
            // before launching the drain loop so it cannot exit on its first check.
            lastVideoFrameAtMs = SystemClock.elapsedRealtime()
            noFrameErrorReported.set(false)
            isRunning.set(true)
            encodingJob = scope?.launch(Dispatchers.Default) {
                drainEncoder(gen)
            }

            Log.i(TAG, "Hardware H.264 Encoder started: ${encWidth}x${encHeight} @ ${config.fps}fps, ${config.bitrateMbps}Mbps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hardware encoder ${encWidth}x${encHeight}: ${e.message}", e)
            onEncoderError("Failed to start H.264 encoder at ${encWidth}x${encHeight}: ${e.message ?: e.javaClass.simpleName}")
            isRunning.set(false)
            try { virtualDisplay?.release() } catch (_: Exception) {}
            virtualDisplay = null
            try { mediaCodec?.stop() } catch (_: Exception) {}
            try { mediaCodec?.release() } catch (_: Exception) {}
            mediaCodec = null
            try { inputSurface?.release() } catch (_: Exception) {}
            inputSurface = null
            false
        }
    }

    // ------------------------------------------------------------------
    // Encoded frame drain
    // ------------------------------------------------------------------

    private fun drainEncoder(gen: Int) {
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRunning.get() && generation.get() == gen) {
            try {
                if (!noFrameErrorReported.get() &&
                    SystemClock.elapsedRealtime() - lastVideoFrameAtMs >= NO_FRAME_TIMEOUT_MS &&
                    noFrameErrorReported.compareAndSet(false, true)
                ) {
                    val message = "H.264 encoder produced no video frames within ${NO_FRAME_TIMEOUT_MS / 1000}s"
                    Log.e(TAG, message)
                    onEncoderError(message)
                }
                synchronized(codecLock) {
                    val codec = mediaCodec ?: return
                    val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)

                    if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val newFormat = codec.outputFormat
                        Log.i(TAG, "Encoder output format changed: $newFormat")
                        val spsBuffer = newFormat.getByteBuffer("csd-0")
                        val ppsBuffer = newFormat.getByteBuffer("csd-1")
                        if (spsBuffer != null && ppsBuffer != null) {
                            val sps = ByteArray(spsBuffer.remaining())
                            spsBuffer.get(sps)
                            val pps = ByteArray(ppsBuffer.remaining())
                            ppsBuffer.get(pps)

                            val combined = ByteArray(sps.size + pps.size)
                            System.arraycopy(sps, 0, combined, 0, sps.size)
                            System.arraycopy(pps, 0, combined, sps.size, pps.size)
                            cachedConfigHeader = combined
                            onSpsPpsAvailable(sps, pps)
                            // SPS/PPS are not a picture: they are prepended to
                            // upcoming IDRs, never broadcast as a video frame.
                            // Clients would treat it as a keyframe with no
                            // picture, fail to decode it, and resync in vain.
                        }
                    } else if (outputBufferIndex >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0) {
                            outputBuffer.position(bufferInfo.offset)
                            outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                            val frameBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(frameBytes)

                            if (isConfig) {
                                // Codec config only - cache it for the next IDR,
                                // but do not send it as a keyframe or advertise it
                                // as the sync frame for new clients.
                                cachedConfigHeader = frameBytes
                            } else {
                                val finalData = if (isKeyFrame && cachedConfigHeader != null && !hasSpsPrefix(frameBytes)) {
                                    val header = cachedConfigHeader!!
                                    val combined = ByteArray(header.size + frameBytes.size)
                                    System.arraycopy(header, 0, combined, 0, header.size)
                                    System.arraycopy(frameBytes, 0, combined, header.size, frameBytes.size)
                                    combined
                                } else {
                                    frameBytes
                                }

                                if (isKeyFrame) {
                                    latestSyncFrame = finalData
                                }

                                lastVideoFrameAtMs = SystemClock.elapsedRealtime()
                                noFrameErrorReported.set(false)
                                onFrameEncoded(finalData, isKeyFrame, bufferInfo.presentationTimeUs)
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get() && generation.get() == gen) {
                    val message = "H.264 encoder output failed: ${e.message ?: e.javaClass.simpleName}"
                    Log.e(TAG, message, e)
                    onEncoderError(message)
                }
                break
            }
        }
    }

    private fun hasSpsPrefix(bytes: ByteArray): Boolean {
        // 4-byte start code 00 00 00 01
        if (bytes.size >= 5 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte()) {
            val nalType = bytes[4].toInt() and 0x1F
            return nalType == 7
        }
        // 3-byte start code 00 00 01
        if (bytes.size >= 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte()) {
            val nalType = bytes[3].toInt() and 0x1F
            return nalType == 7
        }
        return false
    }
}
