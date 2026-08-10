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
    private var syncJob: Job? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = (binder as RecordingService.LocalBinder).getService()
            bound = true
            startSyncingWithService()
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            service = null
            syncJob?.cancel()
        }
    }

    init {
        // Bind immediately so the screen stays in sync even if a recording was
        // started elsewhere (e.g. via the floating bubble) before this screen opened.
        bindToService()
    }

    private fun bindToService() {
        val context = getApplication<Application>()
        val intent = Intent(context, RecordingService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Continuously mirrors the service's real state/error, instead of only checking
     * once right after a button press. This is what keeps the app screen and the
     * floating bubble in sync no matter which one triggered the change.
     */
    private fun startSyncingWithService() {
        syncJob?.cancel()
        syncJob = viewModelScope.launch {
            val svc = service ?: return@launch
            while (true) {
                val svcState = svc.state.value
                if (svcState != _state.value) {
                    _state.value = svcState
                    when (svcState) {
                        RecordingState.RECORDING -> {
                            val startTime = svc.getStartTimeMs()
                            if (startTime > 0) {
                                val baseElapsed = (System.currentTimeMillis() - startTime) / 1000
                                startTicker(fromZero = false, baseSeconds = baseElapsed)
                            } else {
                                startTicker(fromZero = true)
                            }
                        }
                        RecordingState.PAUSED -> tickerJob?.cancel()
                        RecordingState.IDLE -> {
                            tickerJob?.cancel()
                            _elapsedSeconds.value = 0
                        }
                    }
                }
                val svcError = svc.error.value
                if (svcError != _error.value) {
                    _error.value = svcError
                }
                delay(500)
            }
        }
    }

    fun getScreenCaptureIntent(): Intent {
        val context = getApplication<Application>()
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        return manager.createScreenCaptureIntent()
    }

    fun clearError() {
        _error.value = null
        service?.clearError()
    }

    fun startInternalRecording(resultCode: Int, data: Intent) {
        viewModelScope.launch {
            try {
                var attempts = 0
                while (service == null && attempts < 50) {
                    delay(20)
                    attempts++
                }
                val svc = service
                if (svc == null) {
                    _error.value = "Recording service didn't start in time. Please try again."
                    return@launch
                }

                svc.prepareForegroundForCapture()
                delay(100)

                val sampleRate = settingsRepo.sampleRate.first()
                val bitrate = settingsRepo.bitrate.first()
                val stereo = settingsRepo.stereo.first()

                val manager = getApplication<Application>().getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = manager.getMediaProjection(resultCode, data)

                val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
                val outFile = File(recordingRepo.recordingsDir(), fileName)

                svc.startInternalCapture(projection, outFile, sampleRate, bitrate, stereo)
            } catch (e: Exception) {
                _error.value = "Something went wrong starting the recording: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun startMicRecording() {
        viewModelScope.launch {
            try {
                var attempts = 0
                while (service == null && attempts < 50) {
                    delay(20)
                    attempts++
                }

                val sampleRate = settingsRepo.sampleRate.first()
                val bitrate = settingsRepo.bitrate.first()
                val stereo = settingsRepo.stereo.first()

                val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
                val outFile = File(recordingRepo.recordingsDir(), fileName)

                service?.startMicRecording(outFile, sampleRate, bitrate, stereo)
            } catch (e: Exception) {
                _error.value = "Something went wrong starting the recording: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun pause() {
        service?.pause()
    }

    fun resume() {
        service?.resume()
    }

    fun stop() {
        service?.stop()
    }

    private fun startTicker(fromZero: Boolean, baseSeconds: Long = 0) {
        tickerJob?.cancel()
        val baseElapsed = if (fromZero) 0L else baseSeconds
        _elapsedSeconds.value = baseElapsed
        val resumeAt = System.currentTimeMillis()
        tickerJob = viewModelScope.launch {
            while (true) {
                _elapsedSeconds.value = baseElapsed + (System.currentTimeMillis() - resumeAt) / 1000
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        syncJob?.cancel()
        tickerJob?.cancel()
        if (bound) {
            try {
                getApplication<Application>().unbindService(connection)
            } catch (_: Exception) { }
            bound = false
        }
    }
}
