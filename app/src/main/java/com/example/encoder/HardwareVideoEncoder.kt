package com.example.encoder

import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.example.data.model.StreamConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
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
    }

    private var mediaCodec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var encodingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    private var cachedSps: ByteArray? = null
    private var cachedPps: ByteArray? = null

    fun start(mediaProjection: MediaProjection, scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        try {
            val format = MediaFormat.createVideoFormat(MIME_TYPE, config.width, config.height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, config.bitrateBps)
                setInteger(MediaFormat.KEY_FRAME_RATE, config.fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1) // Keyframe every 1s for instant sync
                setInteger(
                    MediaFormat.KEY_BITRATE_MODE,
                    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR
                )
                // Real-time low-latency priority hints
                try {
                    setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
                    setInteger("latency", 0)
                } catch (e: Exception) {
                    Log.d(TAG, "Optional low-latency keys not supported: ${e.message}")
                }
            }

            val codec = MediaCodec.createEncoderByType(MIME_TYPE)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = codec.createInputSurface()
            codec.start()

            this.mediaCodec = codec
            this.inputSurface = surface

            // Create VirtualDisplay bound to the hardware encoder input surface
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "SunshineCastDisplay",
                config.width,
                config.height,
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

            Log.i(TAG, "Hardware H.264 Encoder started: ${config.width}x${config.height} @ ${config.fps}fps, ${config.bitrateMbps}Mbps")
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

    fun getSpsPps(): Pair<ByteArray?, ByteArray?> = Pair(cachedSps, cachedPps)

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
                        cachedSps = sps
                        cachedPps = pps
                        onSpsPpsAvailable(sps, pps)
                        Log.i(TAG, "SPS length: ${sps.size}, PPS length: ${pps.size}")
                    }
                } else if (outputBufferIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)

                        val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                        val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                        if (isConfig) {
                            // Extract config parameters directly from stream if csd wasn't already caught
                            val configBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(configBytes)
                            parseSpsPpsFromConfig(configBytes)
                        } else {
                            val frameBytes = ByteArray(bufferInfo.size)
                            outputBuffer.get(frameBytes)

                            // If this is a keyframe, ensure SPS/PPS headers are present
                            val finalData = if (isKeyFrame && cachedSps != null && cachedPps != null && !hasSpsPrefix(frameBytes)) {
                                val sps = cachedSps!!
                                val pps = cachedPps!!
                                val combined = ByteArray(sps.size + pps.size + frameBytes.size)
                                System.arraycopy(sps, 0, combined, 0, sps.size)
                                System.arraycopy(pps, 0, combined, sps.size, pps.size)
                                System.arraycopy(frameBytes, 0, combined, sps.size + pps.size, frameBytes.size)
                                combined
                            } else {
                                frameBytes
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
        // Checks if bytes begin with Annex-B SPS (0x00, 0x00, 0x00, 0x01, 0x67 or 0x27)
        if (bytes.size < 5) return false
        val nalType = bytes[4].toInt() and 0x1F
        return nalType == 7
    }

    private fun parseSpsPpsFromConfig(bytes: ByteArray) {
        try {
            // Find 00 00 00 01 delimiter
            val delimiters = mutableListOf<Int>()
            for (i in 0 until bytes.size - 4) {
                if (bytes[i] == 0.toByte() && bytes[i + 1] == 0.toByte() &&
                    bytes[i + 2] == 0.toByte() && bytes[i + 3] == 1.toByte()
                ) {
                    delimiters.add(i)
                }
            }
            if (delimiters.size >= 2) {
                val sps = bytes.copyOfRange(delimiters[0], delimiters[1])
                val pps = bytes.copyOfRange(delimiters[1], bytes.size)
                cachedSps = sps
                cachedPps = pps
                onSpsPpsAvailable(sps, pps)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse config bytes: ${e.message}")
        }
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
