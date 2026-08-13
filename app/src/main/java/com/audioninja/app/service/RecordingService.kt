package com.audioninja.app.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.audioninja.app.AudioNinjaApp
import com.audioninja.app.MainActivity
import com.audioninja.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

enum class RecordingState { IDLE, RECORDING, PAUSED }

class RecordingService : Service() {

    private val binder = LocalBinder()
    private var recorder: MediaRecorder? = null
    private var internalEngine: AudioCaptureEngine? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var outputFile: File? = null
    private var recordingStartTimeMs = 0L
    private var foregroundStarted = false

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun getStartTimeMs(): Long = recordingStartTimeMs

    fun clearError() {
        _error.value = null
    }

    fun prepareForegroundForCapture() {
        if (!foregroundStarted) {
            startForegroundNotification()
        }
    }

    fun startMicRecording(file: File, sampleRate: Int, bitrate: Int, stereo: Boolean) {
        try {
            startForegroundNotification()
            outputFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(sampleRate)
                setAudioEncodingBitRate(bitrate)
                setAudioChannels(if (stereo) 2 else 1)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            _error.value = null
            _state.value = RecordingState.RECORDING
        } catch (e: Exception) {
            _error.value = "Couldn't start recording: ${e.message ?: "microphone unavailable"}"
            cleanupAfterFailedStart()
        }
    }

    fun startInternalCapture(
        projection: MediaProjection,
        file: File,
        sampleRate: Int,
        bitrate: Int,
        stereo: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            _error.value = "Internal audio capture needs Android 10 or newer."
            return
        }
        try {
            startForegroundNotification()
            mediaProjection = projection
            outputFile = file

            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    if (_state.value != RecordingState.IDLE) {
                        stop()
                    }
                }
            }
            projectionCallback = callback
            projection.registerCallback(callback, null)

            val engine = AudioCaptureEngine()
            internalEngine = engine
            engine.start(projection, file, sampleRate, bitrate, stereo)

            _error.value = null
            _state.value = RecordingState.RECORDING
        } catch (e: SecurityException) {
            _error.value = "Permission or timing issue starting capture: ${e.message ?: "security error"}"
            cleanupAfterFailedStart()
        } catch (e: Exception) {
            _error.value = "Couldn't start internal capture: ${e.message ?: "unknown error"}"
            cleanupAfterFailedStart()
        }
    }

    fun pause() {
        if (_state.value != RecordingState.RECORDING) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) recorder?.pause()
        } catch (_: Exception) { }
        internalEngine?.pause()
        _state.value = RecordingState.PAUSED
    }

    fun resume() {
        if (_state.value != RecordingState.PAUSED) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) recorder?.resume()
        } catch (_: Exception) { }
        internalEngine?.resume()
        _state.value = RecordingState.RECORDING
    }

    fun stop(): File? {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null

        internalEngine?.stop()
        internalEngine = null

        try {
            projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (_: Exception) { }
        projectionCallback = null

        try {
            mediaProjection?.stop()
        } catch (_: Exception) { }
        mediaProjection = null

        _state.value = RecordingState.IDLE
        foregroundStarted = false

        val finishedFile = outputFile
        if (finishedFile != null && finishedFile.exists()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val shouldTrim = SettingsRepository(applicationContext).trimStartupSilence.first()
                    if (shouldTrim) {
                        AudioPostProcessor.trimStartupSilence(finishedFile)
                    }
                } catch (_: Exception) {
                    // Trimming is a best-effort enhancement — the original recording
                    // is never lost even if this step fails.
                }
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return finishedFile
    }

    private fun cleanupAfterFailedStart() {
        internalEngine?.stop()
        internalEngine = null
        try {
            projectionCallback?.let { mediaProjection?.unregisterCallback(it) }
        } catch (_: Exception) { }
        projectionCallback = null
        try {
            mediaProjection?.stop()
        } catch (_: Exception) { }
        mediaProjection = null
        _state.value = RecordingState.IDLE
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startForegroundNotification() {
        recordingStartTimeMs = System.currentTimeMillis()
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, AudioNinjaApp.RECORDING_CHANNEL_ID)
            .setContentTitle("Audio Ninja")
            .setContentText("Recording in progress")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setUsesChronometer(true)
            .setWhen(recordingStartTimeMs)
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        else 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
        foregroundStarted = true
    }

    companion object {
        fun getMediaProjectionManager(context: Context): MediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
}
