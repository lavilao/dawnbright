package com.example.encoder

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.example.data.model.StreamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class HardwareVideoEncoder(
    private val config: StreamConfig,
    private val onFrameEncoded: (data: ByteArray, isKeyFrame: Boolean, timestampUs: Long) -> Unit,
    private val onSpsPpsAvailable: (sps: ByteArray, pps: ByteArray) -> Unit
) {
    companion object {
        private const val TAG = "HardwareVideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private const val TIMEOUT_US = 10_000L

        @Volatile
        var latestSyncFrame: ByteArray? = null
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encodingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private var cachedConfigHeader: ByteArray? = null

    fun start(mediaProjection: MediaProjection, scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        try {
            // Ensure width and height are even numbers (required by H.264 codecs)
            val encWidth = ((config.width / 2) * 2).coerceAtLeast(320)
            val encHeight = ((config.height / 2) * 2).coerceAtLeast(240)

            val format = MediaFormat.createVideoFormat(MIME_TYPE, encWidth, encHeight).apply {
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
                } catch (_: Exception) {}
            }

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

            val surface = codec.createInputSurface()
            codec.start()

            this.mediaCodec = codec
            this.inputSurface = surface

            // Create VirtualDisplay bound to the hardware encoder input surface
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SunshineCastDisplay",
                encWidth,
                encHeight,
                320, // DPI
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface,
                null,
                null
            )

            // Start reading encoded frames
            encodingJob = scope.launch(Dispatchers.Default) {
                drainEncoder()
            }

            Log.i(TAG, "Hardware H.264 Encoder started: ${encWidth}x${encHeight} @ ${config.fps}fps, ${config.bitrateMbps}Mbps")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hardware encoder", e)
            stop()
        }
    }

    fun requestKeyFrame() {
        try {
            val params = Bundle().apply {
                putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            }
            mediaCodec?.setParameters(params)
            Log.d(TAG, "Keyframe (IDR) requested from hardware encoder")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request sync frame: ${e.message}")
        }
    }

    private fun drainEncoder() {
        val codec = mediaCodec ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (isRunning.get()) {
            try {
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
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.w(TAG, "Error in drainEncoder loop: ${e.message}")
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

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping hardware video encoder")

        encodingJob?.cancel()
        encodingJob = null

        try {
            virtualDisplay?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VirtualDisplay: ${e.message}")
        }
        virtualDisplay = null

        try {
            inputSurface?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing InputSurface: ${e.message}")
        }
        inputSurface = null

        try {
            mediaCodec?.stop()
            mediaCodec?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing MediaCodec: ${e.message}")
        }
        mediaCodec = null
    }
}
