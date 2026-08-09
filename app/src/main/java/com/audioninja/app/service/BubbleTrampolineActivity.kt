package com.audioninja.app.service

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.IBinder
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * Invisible activity that exists only to show the screen-capture permission
 * dialog on behalf of the floating bubble (a Service can't show system
 * permission dialogs directly). Finishes itself right after.
 */
class BubbleTrampolineActivity : Activity() {

    private var recordingService: RecordingService? = null
    private var bound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            recordingService = (binder as? RecordingService.LocalBinder)?.getService()
            bound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            bound = false
            recordingService = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            val intent = Intent(this, RecordingService::class.java)
            bindService(intent, connection, Context.BIND_AUTO_CREATE)
            startForegroundService(intent)

            CoroutineScope(Dispatchers.Main).launch {
                val settingsRepo = SettingsRepository(applicationContext)
                val recordingRepo = RecordingRepository(applicationContext)

                var attempts = 0
                while (recordingService == null && attempts < 50) {
                    delay(20)
                    attempts++
                }
                val svc = recordingService
                if (svc != null) {
                    svc.prepareForegroundForCapture()
                    delay(100)

                    val sampleRate = settingsRepo.sampleRate.first()
                    val bitrate = settingsRepo.bitrate.first()
                    val stereo = settingsRepo.stereo.first()
                    val saveToExternal = settingsRepo.saveToExternal.first()

                    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    val projection = manager.getMediaProjection(resultCode, data)

                    val fileName = "Recording_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.m4a"
                    val outFile = File(recordingRepo.recordingsDir(saveToExternal), fileName)

                    svc.startInternalCapture(projection, outFile, sampleRate, bitrate, stereo)
                }
                if (bound) {
                    try { unbindService(connection) } catch (_: Exception) { }
                    bound = false
                }
                finish()
            }
        } else {
            finish()
        }
    }

    companion object {
        private const val REQUEST_CODE = 4201
    }
}
