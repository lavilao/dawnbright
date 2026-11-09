package com.example.encoder

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AudioStreamer(
    private val onAudioChunk: (data: ByteArray, timestampUs: Long) -> Unit
) {
    companion object {
        private const val TAG = "AudioStreamer"
        const val SAMPLE_RATE = 48000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_STEREO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val isRunning = AtomicBoolean(false)

    @SuppressLint("MissingPermission")
    fun start(mediaProjection: MediaProjection?, scope: CoroutineScope) {
        if (isRunning.getAndSet(true)) return

        try {
            val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufferSize = if (minBufferSize > 0) (minBufferSize * BUFFER_SIZE_FACTOR).coerceAtLeast(4096) else 4096

            val record: AudioRecord? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && mediaProjection != null) {
                try {
                    val playbackConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .build()

                    val format = AudioFormat.Builder()
                        .setEncoding(AUDIO_FORMAT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL_CONFIG)
                        .build()

                    AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(playbackConfig)
                        .setAudioFormat(format)
                        .setBufferSizeInBytes(bufferSize)
                        .build()
                } catch (e: Exception) {
                    Log.w(TAG, "AudioPlaybackCapture not permitted or supported on this device: ${e.message}")
                    null
                }
            } else {
                null
            }

            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.w(TAG, "AudioRecord could not be initialized for internal playback capture")
                isRunning.set(false)
                return
            }

            try {
                record.startRecording()
            } catch (e: Exception) {
                Log.w(TAG, "AudioRecord startRecording failed: ${e.message}")
                isRunning.set(false)
                try { record.release() } catch (_: Exception) {}
                return
            }

            this.audioRecord = record

            recordingJob = scope.launch(Dispatchers.IO) {
                val buffer = ByteArray(2048)
                var ptsUs = System.nanoTime() / 1000

                while (isRunning.get() && isActive) {
                    try {
                        val bytesRead = record.read(buffer, 0, buffer.size)
                        if (bytesRead > 0) {
                            val chunk = ByteArray(bytesRead)
                            System.arraycopy(buffer, 0, chunk, 0, bytesRead)
                            onAudioChunk(chunk, ptsUs)
                            ptsUs += (bytesRead.toLong() * 1_000_000) / (SAMPLE_RATE * 4)
                        } else if (bytesRead < 0) {
                            break
                        }
                    } catch (e: Exception) {
                        break
                    }
                }
            }

            Log.i(TAG, "AudioStreamer started capturing internal game audio @ 48kHz stereo")
        } catch (e: Exception) {
            Log.w(TAG, "AudioStreamer start caught error: ${e.message}")
            stop()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Log.i(TAG, "Stopping AudioStreamer")

        recordingJob?.cancel()
        recordingJob = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        audioRecord = null
    }
}
