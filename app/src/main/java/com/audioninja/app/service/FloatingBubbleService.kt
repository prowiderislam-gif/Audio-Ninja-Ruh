package com.audioninja.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Draggable overlay bubble with a live-counting timer while a recording is active.
 * Binds to RecordingService (if one is running) to read its state and start time.
 */
class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var timerText: TextView? = null

    private var recordingService: RecordingService? = null
    private var boundToService = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTimerDisplay()
            handler.postDelayed(this, 1000)
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            recordingService = (binder as? RecordingService.LocalBinder)?.getService()
            boundToService = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            boundToService = false
            recordingService = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
        tryBindToRecordingService()
        handler.post(tickRunnable)
    }

    private fun tryBindToRecordingService() {
        if (!boundToService) {
            val intent = Intent(this, RecordingService::class.java)
            bindService(intent, connection, 0)
        }
    }

    private fun updateTimerDisplay() {
        val svc = recordingService
        if (svc == null) {
            tryBindToRecordingService()
            timerText?.text = "--:--"
            return
        }
        val state = svc.state.value
        if (state == RecordingState.IDLE) {
            timerText?.text = "--:--"
            return
        }
        val elapsedMs = System.currentTimeMillis() - svc.getStartTimeMs()
        val totalSeconds = (elapsedMs / 1000).coerceAtLeast(0)
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        timerText?.text = String.format("%02d:%02d", m, s)
    }

    private fun addBubble() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#B0102A"))
            setPadding(20, 12, 20, 12)
        }

        val icon = TextView(this).apply {
            text = "🎙"
            textSize = 18f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }

        val timer = TextView(this).apply {
            text = "--:--"
            textSize = 12f
            setTextColor(Color.WHITE)
            gravity = android.view.Gravity.CENTER
        }
        timerText = timer

        container.addView(icon)
        container.addView(timer)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        container.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - touchX).toInt()
                        params.y = initialY + (event.rawY - touchY).toInt()
                        windowManager.updateViewLayout(container, params)
                    }
                }
                return false
            }
        })

        windowManager.addView(container, params)
        bubbleView = container
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        if (boundToService) {
            try { unbindService(connection) } catch (_: Exception) { }
        }
        bubbleView?.let { windowManager.removeView(it) }
    }
}
