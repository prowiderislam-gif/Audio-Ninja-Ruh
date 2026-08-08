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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

enum class RecordingState { IDLE, RECORDING, PAUSED }

class RecordingService : Service() {

    private val binder = LocalBinder()
    private var recorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var outputFile: File? = null
    private var startTimeMs = 0L
    private var pausedAccumMs = 0L

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs

    inner class LocalBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    fun startMicRecording(file: File, sampleRate: Int, bitrate: Int, stereo: Boolean) {
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
        startTimeMs = System.currentTimeMillis()
        pausedAccumMs = 0L
        _state.value = RecordingState.RECORDING
    }

    fun startInternalCapture(
        projection: MediaProjection,
        file: File,
        sampleRate: Int,
        bitrate: Int,
        stereo: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        startForegroundNotification()
        mediaProjection = projection
        outputFile = file
        startTimeMs = System.currentTimeMillis()
        pausedAccumMs = 0L
        _state.value = RecordingState.RECORDING
    }

    fun pause() {
        if (_state.value != RecordingState.RECORDING) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) recorder?.pause()
        } catch (_: Exception) { }
        _state.value = RecordingState.PAUSED
    }

    fun resume() {
        if (_state.value != RecordingState.PAUSED) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) recorder?.resume()
        } catch (_: Exception) { }
        _state.value = RecordingState.RECORDING
    }

    fun stop(): File? {
        try {
            recorder?.stop()
            recorder?.release()
        } catch (_: Exception) { }
        recorder = null
        mediaProjection?.stop()
        mediaProjection = null
        _state.value = RecordingState.IDLE
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        return outputFile
    }

    private fun startForegroundNotification() {
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
            .build()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        else 0

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, type)
        } else {
            startForeground(1, notification)
        }
    }

    companion object {
        fun getMediaProjectionManager(context: Context): MediaProjectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
}
