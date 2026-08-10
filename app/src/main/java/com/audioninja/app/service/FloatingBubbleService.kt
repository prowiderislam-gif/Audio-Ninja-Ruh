package com.audioninja.app.service

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.audioninja.app.R

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var timerText: TextView? = null
    private var panelTimerText: TextView? = null
    private var panelStatusText: TextView? = null
    private var recordButton: Button? = null

    private var recordingService: RecordingService? = null
    private var boundToService = false
    private var isPanelExpanded = false

    private val handler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            updateTimerDisplays()
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
            bindService(Intent(this, RecordingService::class.java), connection, 0)
        }
    }

    private fun updateTimerDisplays() {
        val svc = recordingService
        if (svc == null) {
            tryBindToRecordingService()
            timerText?.text = "🥷"
            panelTimerText?.text = "00:00:00"
            panelStatusText?.text = "Ready to record"
            return
        }
        val state = svc.state.value
        if (state == RecordingState.IDLE) {
            timerText?.text = "🥷"
            panelTimerText?.text = "00:00:00"
            panelStatusText?.text = "Ready to record"
            recordButton?.text = "Record"
            return
        }
        val elapsedMs = (System.currentTimeMillis() - svc.getStartTimeMs()).coerceAtLeast(0)
        val totalSeconds = elapsedMs / 1000
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        val shortTime = String.format("%02d:%02d", m, s)
        val longTime = String.format("%02d:%02d:%02d", h, m, s)
        timerText?.text = shortTime
        panelTimerText?.text = longTime
        panelStatusText?.text = if (state == RecordingState.PAUSED) "Paused" else "Recording..."
        recordButton?.text = if (state == RecordingState.PAUSED) "Resume" else "Pause"
    }

    // ---------- Collapsed bubble: gradient glass ring with logo ----------

    private fun addBubble() {
        val outerSize = 148

        val glowRing = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = outerSize / 2f
            colors = intArrayOf(
                Color.parseColor("#66FF2E4D"),
                Color.parseColor("#00000000")
            )
        }

        val glassRing = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1A0508"))
            setStroke(3, Color.parseColor("#FF2E4D"))
        }

        val container = FrameLayout(this)

        val glow = ImageView(this).apply {
            setImageDrawable(glowRing)
            layoutParams = FrameLayout.LayoutParams(outerSize, outerSize)
        }

        val logoSize = 96
        val logoFrame = FrameLayout(this).apply {
            background = glassRing
            layoutParams = FrameLayout.LayoutParams(logoSize, logoSize, Gravity.CENTER)
        }

        val logoImage = ImageView(this).apply {
            val resId = resources.getIdentifier("logo", "drawable", packageName)
            if (resId != 0) setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = FrameLayout.LayoutParams(logoSize - 10, logoSize - 10, Gravity.CENTER)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLACK)
            }
        }
        logoFrame.addView(logoImage)

        val timer = TextView(this).apply {
            text = "🥷"
            textSize = 10f
            setTextColor(Color.parseColor("#FF2E4D"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = 2 }
        }
        timerText = timer

        container.addView(glow)
        container.addView(logoFrame)
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
            y = 300
        }
        bubbleParams = params

        container.setOnTouchListener(object : View.OnTouchListener {
            var initialX = 0
            var initialY = 0
            var touchX = 0f
            var touchY = 0f
            var moved = false

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        moved = false
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) moved = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager.updateViewLayout(container, params)
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) toggleExpandedPanel()
                    }
                }
                return true
            }
        })

        windowManager.addView(container, params)
        bubbleView = container
    }

    // ---------- Expanded "Ninja Controls" panel: glass card ----------

    private fun toggleExpandedPanel() {
        if (isPanelExpanded) closePanel() else openPanel()
    }

    private fun openPanel() {
        if (panelView != null) return
        isPanelExpanded = true

        val cardBg = GradientDrawable().apply {
            cornerRadius = 32f
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.TL_BR
            colors = intArrayOf(
                Color.parseColor("#E6180810"),
                Color.parseColor("#E60A0203")
            )
            setStroke(2, Color.parseColor("#66FF2E4D"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            setPadding(40, 32, 40, 32)
        }

        val headerRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logoMini = ImageView(this).apply {
            val resId = resources.getIdentifier("logo", "drawable", packageName)
            if (resId != 0) setImageResource(resId)
            scaleType = ImageView.ScaleType.CENTER_CROP
            layoutParams = LinearLayout.LayoutParams(40, 40)
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.BLACK)
            }
        }
        val title = TextView(this).apply {
            text = "  NINJA CONTROLS"
            setTextColor(Color.parseColor("#FF2E4D"))
            textSize = 15f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = 10
            }
        }
        val closeBtn = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(16, 0, 0, 0)
            setOnClickListener { closePanel() }
        }
        headerRow.addView(logoMini)
        headerRow.addView(title)
        headerRow.addView(closeBtn)

        val timer = TextView(this).apply {
            text = "00:00:00"
            setTextColor(Color.WHITE)
            textSize = 30f
            gravity = Gravity.CENTER
            setPadding(0, 24, 0, 4)
        }
        panelTimerText = timer

        val status = TextView(this).apply {
            text = "Ready to record"
            setTextColor(Color.parseColor("#B9989C"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 20)
        }
        panelStatusText = status

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val recordBg = GradientDrawable().apply {
            cornerRadius = 40f
            gradientType = GradientDrawable.LINEAR_GRADIENT
            orientation = GradientDrawable.Orientation.LEFT_RIGHT
            colors = intArrayOf(Color.parseColor("#FF2E4D"), Color.parseColor("#B0102A"))
        }
        val record = Button(this).apply {
            text = "Record"
            setTextColor(Color.WHITE)
            background = recordBg
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = 16
            }
            setOnClickListener { onRecordButtonTapped() }
        }
        recordButton = record

        val stopBg = GradientDrawable().apply {
            cornerRadius = 16f
            setColor(Color.parseColor("#2A0E12"))
            setStroke(2, Color.parseColor("#FF2E4D"))
        }
        val stop = Button(this).apply {
            text = "■"
            setTextColor(Color.parseColor("#FF2E4D"))
            background = stopBg
            setOnClickListener { onStopButtonTapped() }
        }

        buttonRow.addView(record)
        buttonRow.addView(stop)

        val helpText = TextView(this).apply {
            text = "ⓘ  Floating Bubble Help & Android Info"
            setTextColor(Color.parseColor("#B9989C"))
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 0)
            setOnClickListener {
                android.widget.Toast.makeText(
                    this@FloatingBubbleService,
                    "Android shows a system permission dialog each time internal-audio recording starts — this is required and can't be skipped.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }

        root.addView(headerRow)
        root.addView(timer)
        root.addView(status)
        root.addView(buttonRow)
        root.addView(helpText)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val bp = bubbleParams
        val params = WindowManager.LayoutParams(
            680,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((bp?.x ?: 0) - 500).coerceAtLeast(0)
            y = (bp?.y ?: 300) + 160
        }

        windowManager.addView(root, params)
        panelView = root
        updateTimerDisplays()
    }

    private fun closePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        panelView = null
        panelTimerText = null
        panelStatusText = null
        recordButton = null
        isPanelExpanded = false
    }

    private fun onRecordButtonTapped() {
        val svc = recordingService
        when (svc?.state?.value) {
            RecordingState.IDLE, null -> {
                val intent = Intent(this, BubbleTrampolineActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            }
            RecordingState.RECORDING -> svc.pause()
            RecordingState.PAUSED -> svc.resume()
        }
    }

    private fun onStopButtonTapped() {
        recordingService?.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        if (boundToService) {
            try { unbindService(connection) } catch (_: Exception) { }
        }
        closePanel()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
    }
}
