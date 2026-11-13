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
    private val onFrameSizeChanged: (width: Int, height: Int) -> Unit = { _, _ -> }
) {
    companion object {
        private const val TAG = "HardwareVideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val TIMEOUT_US = 10_000L

        /** Cooldown after a rotation before rebuilding (rotation events fire in bursts). */
        private const val ROTATION_SETTLE_MS = 400L

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
        if (isRunning.getAndSet(true)) return

        this.mediaProjection = mediaProjection
        this.scope = scope
        this.getDisplaySize = getDisplaySize

        val display = try { getDisplaySize?.invoke() } catch (_: Exception) { null }

        var encWidth = 0
        var encHeight = 0
        synchronized(codecLock) {
            val size = computeFrameSize(display ?: Point(config.width, config.height))
            encWidth = size.first
            encHeight = size.second
            if (startPipelineLocked(encWidth, encHeight, generation.get())) {
                frameWidth = encWidth
                frameHeight = encHeight
            } else {
                stop()
                return
            }
        }
        onFrameSizeChanged(encWidth, encHeight)
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
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping hardware video encoder")

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
                Log.e(TAG, "Encoder rebuild failed after rotation; stream will stay at old size until restarted")
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
        val maxW = ((config.width / 2) * 2).coerceAtLeast(320)
        val maxH = ((config.height / 2) * 2).coerceAtLeast(240)
        if (display.x <= 0 || display.y <= 0) return Pair(maxW, maxH)
        val scale = minOf(
            maxW.toFloat() / display.x,
            maxH.toFloat() / display.y
        )
        val w = ((display.x * scale).toInt() / 2 * 2).coerceAtLeast(320)
        val h = ((display.y * scale).toInt() / 2 * 2).coerceAtLeast(240)
        return Pair(w, h)
    }

    private fun buildVideoFormat(encWidth: Int, encHeight: Int): MediaFormat {
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

            // Attempt CBR rate control mode first
            try {
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
            } catch (_: Exception) {}

            // Low-latency priority hints
            try {
                setInteger(MediaFormat.KEY_PRIORITY, 0)
                setInteger("latency", 0)
                setInteger(MediaFormat.KEY_OPERATING_RATE, config.fps)
                // Android 11+: ask the encoder to disable reordering (B-frames),
                // which otherwise adds several frames of pipeline latency.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            } catch (_: Exception) {}
        }
    }

    private fun createAndConfigureCodec(format: MediaFormat): MediaCodec {
        val codec = MediaCodec.createEncoderByType(MIME_TYPE)
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        } catch (e: Exception) {
            Log.w(TAG, "Configure with CBR failed, falling back to VBR: ${e.message}")
            format.setInteger(
                MediaFormat.KEY_BITRATE_MODE,
                MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR
            )
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        }
        return codec
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

            encodingJob = scope?.launch(Dispatchers.Default) {
                drainEncoder(gen)
            }

            Log.i(TAG, "Hardware H.264 Encoder started: ${encWidth}x${encHeight} @ ${config.fps}fps, ${config.bitrateMbps}Mbps")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hardware encoder ${encWidth}x${encHeight}: ${e.message}", e)
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

                            // Immediately broadcast config packet
                            onFrameEncoded(combined, true, System.nanoTime() / 1000)
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
                                cachedConfigHeader = frameBytes
                                latestSyncFrame = frameBytes
                                onFrameEncoded(frameBytes, true, bufferInfo.presentationTimeUs)
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

                                onFrameEncoded(finalData, isKeyFrame, bufferInfo.presentationTimeUs)
                            }
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                }
            } catch (e: Exception) {
                if (isRunning.get() && generation.get() == gen) {
                    Log.w(TAG, "Error in drainEncoder loop: " + e.message)
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
