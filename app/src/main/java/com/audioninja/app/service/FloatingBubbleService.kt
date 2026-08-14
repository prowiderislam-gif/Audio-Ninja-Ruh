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
import com.audioninja.app.MainActivity
import com.audioninja.app.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var removeTargetView: View? = null
    private var bubbleParams: WindowManager.LayoutParams? = null

    private var timestampBadge: TextView? = null
    private var panelTimerText: TextView? = null
    private var panelStatusText: TextView? = null
    private var recordButton: Button? = null

    private var recordingService: RecordingService? = null
    private var boundToService = false
    private var isPanelExpanded = false
    private var isDragging = false

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

    private var lastKnownState: RecordingState? = null

    private fun updateTimerDisplays() {
        val svc = recordingService
        if (svc == null) {
            tryBindToRecordingService()
            timestampBadge?.visibility = View.GONE
            panelTimerText?.text = "00:00:00"
            panelStatusText?.text = "Ready to record"
            return
        }
        val state = svc.state.value

        if (lastKnownState != RecordingState.IDLE && state == RecordingState.IDLE && isPanelExpanded) {
            closePanel()
        }
        lastKnownState = state

        if (state == RecordingState.IDLE) {
            timestampBadge?.visibility = View.GONE
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

        timestampBadge?.visibility = View.VISIBLE
        timestampBadge?.text = shortTime

        panelTimerText?.text = longTime
        panelStatusText?.text = if (state == RecordingState.PAUSED) "Paused" else "Recording..."
        recordButton?.text = if (state == RecordingState.PAUSED) "Resume" else "Pause"
    }

    // ---------- Collapsed bubble: gradient glass ring + logo + timestamp badge ----------

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

        val badgeBg = GradientDrawable().apply {
            cornerRadius = 24f
            setColor(Color.parseColor("#E6000000"))
            setStroke(2, Color.parseColor("#FF2E4D"))
        }
        val badge = TextView(this).apply {
            text = "00:00"
            textSize = 11f
            setTextColor(Color.parseColor("#FF4D68"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            background = badgeBg
            setPadding(14, 4, 14, 4)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = -8 }
        }
        timestampBadge = badge

        container.addView(glow)
        container.addView(logoFrame)
        container.addView(badge)

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

        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            openMainApp()
        }

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
                        longPressTriggered = false
                        handler.postDelayed(longPressRunnable, 500)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - touchX).toInt()
                        val dy = (event.rawY - touchY).toInt()
                        if (kotlin.math.abs(dx) > 12 || kotlin.math.abs(dy) > 12) {
                            if (!moved) {
                                moved = true
                                handler.removeCallbacks(longPressRunnable)
                                if (isPanelExpanded) closePanel()
                                showRemoveTarget()
                                isDragging = true
                            }
                        }
                        if (moved) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(container, params)
                            updateRemoveTargetHighlight(event.rawX, event.rawY)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        handler.removeCallbacks(longPressRunnable)
                        if (isDragging) {
                            isDragging = false
                            val overTarget = isOverRemoveTarget(event.rawX, event.rawY)
                            hideRemoveTarget()
                            if (overTarget) {
                                stopSelf()
                                return true
                            }
                            snapToNearestEdge(container, params)
                        } else if (!moved && !longPressTriggered) {
                            toggleExpandedPanel()
                        }
                        moved = false
                    }
                }
                return true
            }
        })

        windowManager.addView(container, params)
        bubbleView = container
    }

    /** Animates the bubble to whichever screen edge (left/right) it's closer to. */
    private fun snapToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubbleWidth = view.width.takeIf { it > 0 } ?: 148
        val midpoint = screenWidth / 2

        val targetX = if (params.x + bubbleWidth / 2 < midpoint) 0 else screenWidth - bubbleWidth

        val startX = params.x
        val steps = 12
        var step = 0
        val animator = object : Runnable {
            override fun run() {
                step++
                val progress = step.toFloat() / steps
                val eased = 1 - (1 - progress) * (1 - progress) // ease-out
                params.x = (startX + (targetX - startX) * eased).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                } catch (_: Exception) {
                    return
                }
                if (step < steps) handler.postDelayed(this, 12)
            }
        }
        handler.post(animator)
    }

    private fun openMainApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    // ---------- Drag-to-remove target ----------

    private fun showRemoveTarget() {
        if (removeTargetView != null) return

        val targetSize = 180

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC1A0508"))
            setStroke(3, Color.parseColor("#FF2E4D"))
        }

        val target = FrameLayout(this).apply {
            background = bg
        }
        val cross = TextView(this).apply {
            text = "✕"
            textSize = 26f
            setTextColor(Color.parseColor("#FF2E4D"))
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        target.addView(cross)

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            targetSize,
            targetSize,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 120
        }

        windowManager.addView(target, params)
        removeTargetView = target
    }

    private fun isOverRemoveTarget(rawX: Float, rawY: Float): Boolean {
        val target = removeTargetView ?: return false
        val location = IntArray(2)
        target.getLocationOnScreen(location)
        val left = location[0]
        val top = location[1]
        val right = left + target.width
        val bottom = top + target.height
        return rawX in left.toFloat()..right.toFloat() && rawY in top.toFloat()..bottom.toFloat()
    }

    private fun updateRemoveTargetHighlight(rawX: Float, rawY: Float) {
        val target = removeTargetView as? FrameLayout ?: return
        val isOver = isOverRemoveTarget(rawX, rawY)
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isOver) Color.parseColor("#FF2E4D") else Color.parseColor("#CC1A0508"))
            setStroke(3, Color.parseColor("#FF2E4D"))
        }
        target.background = bg
    }

    private fun hideRemoveTarget() {
        removeTargetView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        removeTargetView = null
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
            setOnClickListener {
                onStopButtonTapped()
                closePanel()
            }
        }

        buttonRow.addView(record)
        buttonRow.addView(stop)

        root.addView(headerRow)
        root.addView(timer)
        root.addView(status)
        root.addView(buttonRow)

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

        val outsideCatcher = FrameLayout(this).apply {
            setOnClickListener { closePanel() }
        }
        val catcherParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        windowManager.addView(outsideCatcher, catcherParams)
        windowManager.addView(root, params)
        panelView = root
        outsideCatcherView = outsideCatcher
        updateTimerDisplays()
    }

    private var outsideCatcherView: View? = null

    private fun closePanel() {
        panelView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        outsideCatcherView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        panelView = null
        outsideCatcherView = null
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
        hideRemoveTarget()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
    }
}
