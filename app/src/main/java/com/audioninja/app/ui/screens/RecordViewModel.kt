package com.audioninja.app.ui.screens

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.data.SettingsRepository
import com.audioninja.app.service.RecordingService
import com.audioninja.app.service.RecordingState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class RecordViewModel(app: Application) : AndroidViewModel(app) {

    private val settingsRepo = SettingsRepository(app)
    private val recordingRepo = RecordingRepository(app)

    private var service: RecordingService? = null
    private var bound = false

    private val _state = MutableStateFlow(RecordingState.IDLE)
    val state: StateFlow<RecordingState> = _state

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var tickerJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as RecordingService.LocalBinder).getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
        }
    }

    fun getScreenCaptureIntent(): Intent {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun clearError() {
        _error.value = null
    }

    fun startInternalRecording(resultCode: Int, data: Intent) {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        context.startForegroundService(intent)

        viewModelScope.launch {
            val sampleRate = settingsRepo.sampleRate.first()
            val bitrate = settingsRepo.bitrate.first()
            val stereo = settingsRepo.stereo.first()
            val saveToExternal = settingsRepo.saveToExternal.first()

            var attempts = 0
            while (service == null && attempts < 50) {
                delay(20)
                attempts++
            }

            val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, data)

            val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
            val outFile = File(recordingRepo.recordingsDir(saveToExternal), fileName)

            service?.startInternalCapture(projection, outFile, sampleRate, bitrate, stereo)

            // Give the service a brief moment to either succeed or fail, then reflect
            // its real state/error instead of optimistically assuming success.
            delay(400)
            val actualState = service?.state?.value ?: RecordingState.IDLE
            _state.value = actualState
            _error.value = service?.error?.value
            if (actualState == RecordingState.RECORDING) {
                startTicker(fromZero = true)
            }
        }
    }

    fun startMicRecording() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        context.startForegroundService(intent)

        viewModelScope.launch {
            val sampleRate = settingsRepo.sampleRate.first()
            val bitrate = settingsRepo.bitrate.first()
            val stereo = settingsRepo.stereo.first()
            val saveToExternal = settingsRepo.saveToExternal.first()

            var attempts = 0
            while (service == null && attempts < 50) {
                delay(20)
                attempts++
            }

            val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
            val outFile = File(recordingRepo.recordingsDir(saveToExternal), fileName)

            service?.startMicRecording(outFile, sampleRate, bitrate, stereo)

            delay(400)
            val actualState = service?.state?.value ?: RecordingState.IDLE
            _state.value = actualState
            _error.value = service?.error?.value
            if (actualState == RecordingState.RECORDING) {
                startTicker(fromZero = true)
            }
        }
    }

    fun pause() {
        service?.pause()
        _state.value = RecordingState.PAUSED
        tickerJob?.cancel()
    }

    fun resume() {
        service?.resume()
        _state.value = RecordingState.RECORDING
        startTicker(fromZero = false)
    }

    fun stop() {
        service?.stop()
        _state.value = RecordingState.IDLE
        tickerJob?.cancel()
        _elapsedSeconds.value = 0
        if (bound) {
            getApplication<Application>().unbindService(connection)
            bound = false
        }
    }

    private fun startTicker(fromZero: Boolean) {
        tickerJob?.cancel()
        if (fromZero) _elapsedSeconds.value = 0
        val baseElapsed = _elapsedSeconds.value
        val resumeAt = System.currentTimeMillis()
        tickerJob = viewModelScope.launch {
            while (true) {
                _elapsedSeconds.value = baseElapsed + (System.currentTimeMillis() - resumeAt) / 1000
                delay(1000)
            }
        }
    }
}
