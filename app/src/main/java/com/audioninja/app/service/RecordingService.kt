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

/**
 * Handles both microphone recording (fully supported on all API 26+ devices)
 * and internal/system audio capture via AudioPlaybackCaptureConfiguration.
 *
 * Internal capture requires API 29+, an active MediaProjection grant from the
 * user, and only captures audio from apps using MEDIA/GAME/UNKNOWN usage that
 * haven't opted out (system restriction, not something the app can bypass).
 */
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

    /**
     * Starts internal audio capture. Caller must first obtain a MediaProjection
     * grant (via MediaProjectionManager.createScreenCaptureIntent()) and pass the
     * resulting projection here — this cannot be done from a background service alone.
     */
    fun startInternalCapture(
        projection: MediaProjection,
        file: File,
        sampleRate: Int,
        bitrate: Int,
        stereo: Boolean
    ) {
        if (Build.VERSION.SDK_INT
