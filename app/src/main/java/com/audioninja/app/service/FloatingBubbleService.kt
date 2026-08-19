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
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.audioninja.app.MainActivity
import com.audioninja.app.data.Playlist
import com.audioninja.app.data.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class BubbleMode { RECORDING, MUSIC }

class FloatingBubbleService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: ImageView? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private var timestampBadge: TextView? = null
    private var badgeParams: WindowManager.LayoutParams? = null
    private var removeTargetView: View? = null

    private var currentMode = BubbleMode.RECORDING
    private var isMenuExpanded = false
    private var isDragging = false

    private var arcButtons: MutableList<BubbleArcButton> = mutableListOf()
    private var arcLabels: MutableList<TextView> = mutableListOf()

    private var recordingService: RecordingService? = null
    private var recordingBound = false
    private val recordingConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            recordingService = (binder as? RecordingService.LocalBinder)?.getService()
            recordingBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            recordingBound = false
            recordingService = null
        }
    }

    private var musicService: MusicPlayerService? = null
    private var musicBound = false
    private val musicConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            musicService = (binder as? MusicPlayerService.LocalBinder)?.getService()
            musicBound = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            musicBound = false
            musicService = null
        }
    }

    private lateinit var playlistRepo: PlaylistRepository
    private var pinnedPlaylists: List<Playlist> = emptyList()

    private val handler = Handler(Looper.getMainLooper())
    private var lastKnownState: RecordingState? = null
    private var lastMusicState: MusicPlaybackState? = null
    private val tickRunnable = object : Runnable {
        override fun run() {
            autoSyncMode()
            updateBubbleAndBadge()
            handler.postDelayed(this, 500)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        playlistRepo = PlaylistRepository(applicationContext)
        addBubble()
        bindService(Intent(this, RecordingService::class.java), recordingConnection, 0)
        tryBindMusicService()
        loadPinnedPlaylists()
        handler.post(tickRunnable)
    }

    private fun tryBindMusicService() {
        if (!musicBound) {
            try {
                bindService(Intent(this, MusicPlayerService::class.java), musicConnection, 0)
            } catch (_: Exception) { }
        }
    }

    private fun loadPinnedPlaylists() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val ids = playlistRepo.pinnedPlaylistIds.first()
                val all = playlistRepo.playlists.first()
                pinnedPlaylists = ids.mapNotNull { id -> all.firstOrNull { pl -> pl.id == id } }
            } catch (_: Exception) { }
        }
    }

    /**
     * Detects real playback/recording activity and keeps the bubble's mode in
     * sync automatically — no manual toggle needed for this to stay correct.
     * Recording always wins: if a recording is active, music is stopped and
     * the bubble switches to Recording mode immediately, regardless of what
     * was happening before.
     */
    private fun autoSyncMode() {
        val recState = recordingService?.state?.value ?: RecordingState.IDLE
        val musState = musicService?.state?.value ?: MusicPlaybackState.IDLE

        if (recState == RecordingState.RECORDING || recState == RecordingState.PAUSED) {
            if (currentMode != BubbleMode.RECORDING) {
                currentMode = BubbleMode.RECORDING
                refreshBubbleImage()
                collapseMenu()
            }
            if (lastMusicState != MusicPlaybackState.IDLE && musState != MusicPlaybackState.IDLE) {
                musicService?.stop()
            }
        } else if (musState == MusicPlaybackState.PLAYING || musState == MusicPlaybackState.PAUSED) {
            if (currentMode != BubbleMode.MUSIC) {
                currentMode = BubbleMode.MUSIC
                refreshBubbleImage()
                collapseMenu()
                loadPinnedPlaylists()
            }
        }
        lastMusicState = musState
    }

    // ---------- Main bubble ----------

    private fun refreshBubbleImage() {
        val name = if (currentMode == BubbleMode.MUSIC) "music_mode_bubble" else "record_mode_bubble"
        val resId = resources.getIdentifier(name, "drawable", packageName)
        if (resId != 0) bubbleView?.setImageResource(resId)
    }

    private fun addBubble() {
        val bubbleSize = 150

        val imageView = ImageView(this)
        bubbleView = imageView
        refreshBubbleImage()

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
        }
        timestampBadge = badge

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

        val params = WindowManager.LayoutParams(
            bubbleSize, bubbleSize, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 300
        }
        bubbleParams = params

        val badgeP = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = params.x
            y = params.y + bubbleSize - 10
        }
        badgeParams = badgeP

        var longPressTriggered = false
        val longPressRunnable = Runnable {
            longPressTriggered = true
            openMainApp()
        }

        imageView.setOnTouchListener(object : View.OnTouchListener {
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
                                if (isMenuExpanded) collapseMenu()
                                showRemoveTarget()
                                isDragging = true
                            }
                        }
                        if (moved) {
                            params.x = initialX + dx
                            params.y = initialY + dy
                            windowManager.updateViewLayout(imageView, params)
                            syncBadgePosition()
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
                            snapToNearestEdge(imageView, params)
                        } else if (!moved && !longPressTriggered) {
                            toggleMenu()
                        }
                        moved = false
                    }
                }
                return true
            }
        })

        windowManager.addView(imageView, params)
        windowManager.addView(badge, badgeP)
    }

    private fun syncBadgePosition() {
        val bp = bubbleParams ?: return
        val badgeP = badgeParams ?: return
        badgeP.x = bp.x
        badgeP.y = bp.y + 140
        timestampBadge?.let {
            try { windowManager.updateViewLayout(it, badgeP) } catch (_: Exception) { }
        }
    }

    private fun snapToNearestEdge(view: View, params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleWidth = view.width.takeIf { it > 0 } ?: 150
        val midpoint = screenWidth / 2
        val targetX = if (params.x + bubbleWidth / 2 < midpoint) 0 else screenWidth - bubbleWidth

        val startX = params.x
        val steps = 12
        var step = 0
        val animator = object : Runnable {
            override fun run() {
                step++
                val progress = step.toFloat() / steps
                val eased = 1 - (1 - progress) * (1 - progress)
                params.x = (startX + (targetX - startX) * eased).toInt()
                try {
                    windowManager.updateViewLayout(view, params)
                    syncBadgePosition()
                } catch (_: Exception) {
                    return
                }
                if (step < steps) handler.postDelayed(this, 12)
            }
        }
        handler.post(animator)
    }

    private fun openMainApp() {
        startActivity(Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    // ---------- Arc menu ----------

    private fun toggleMenu() {
        if (isMenuExpanded) collapseMenu() else expandMenu()
    }

    private fun expandMenu() {
        if (isMenuExpanded) return
        isMenuExpanded = true
        val bp = bubbleParams ?: return
        val screenWidth = resources.displayMetrics.widthPixels
        val bubbleCenterX = bp.x + 75
        val bubbleCenterY = bp.y + 75
        val onLeftEdge = bubbleCenterX < screenWidth / 2

        arcButtons.forEach { it.hide() }
        arcButtons.clear()
        arcLabels.forEach { try { windowManager.removeView(it) } catch (_: Exception) { } }
        arcLabels.clear()

        val radius = 260
        // Angles chosen so the arc always bows INTO the screen: pointing right
        // (positive cos) when the bubble sits on the left edge, and pointing
        // left (negative cos) when it sits on the right edge. Offsets are
        // always added directly — no sign-flipping needed.
        val angles = if (onLeftEdge)
            listOf(-60.0, -20.0, 20.0, 60.0)
        else
            listOf(-120.0, -160.0, 160.0, 120.0)

        val buttonNames = if (currentMode == BubbleMode.RECORDING) {
            val svc = recordingService
            val startOrPause = if (svc?.state?.value == RecordingState.RECORDING) "pause_record_key" else "start_record_key"
            listOf(startOrPause, "stop_record_key", "music_switch_key")
        } else {
            listOf("playlist_slot", "playlist_slot", "playlist_slot", "record_switch_key")
        }

        buttonNames.forEachIndexed { index, drawableName ->
            val angleDeg = angles.getOrElse(index) { 0.0 }
            val angleRad = Math.toRadians(angleDeg)
            val offsetX = (radius * kotlin.math.cos(angleRad)).toInt()
            val offsetY = (radius * kotlin.math.sin(angleRad)).toInt()

            val actualDrawable = if (drawableName == "playlist_slot") "playlist${index + 1}_key" else drawableName

            val button = BubbleArcButton(this, windowManager, actualDrawable, sizePx = 130)
            val targetX = bubbleCenterX + offsetX
            val targetY = bubbleCenterY + offsetY

            button.show(targetX, targetY) {
                handleArcButtonTap(drawableName, index)
            }
            arcButtons.add(button)

            // Real playlist name overlay, so the bubble shows what you named
            // it in the app instead of a generic "Playlist 1/2/3" label.
            if (drawableName == "playlist_slot") {
                val playlistName = pinnedPlaylists.getOrNull(index)?.name ?: "Playlist ${index + 1}"
                addPlaylistLabel(playlistName, targetX, targetY)
            }
        }
    }

    private fun addPlaylistLabel(name: String, centerX: Int, centerY: Int) {
        val labelBg = GradientDrawable().apply {
            cornerRadius = 20f
            setColor(Color.parseColor("#CC000000"))
        }
        val label = TextView(this).apply {
            text = if (name.length > 12) name.take(11) + "…" else name
            textSize = 10f
            setTextColor(Color.WHITE)
            background = labelBg
            setPadding(12, 3, 12, 3)
            maxLines = 1
        }

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
            x = centerX - 90
            y = centerY + 65
        }

        try {
            windowManager.addView(label, params)
            arcLabels.add(label)
        } catch (_: Exception) { }
    }

    private fun collapseMenu() {
        isMenuExpanded = false
        arcButtons.forEach { it.hide() }
        arcButtons.clear()
        arcLabels.forEach { try { windowManager.removeView(it) } catch (_: Exception) { } }
        arcLabels.clear()
    }

    private fun handleArcButtonTap(drawableName: String, index: Int) {
        when {
            currentMode == BubbleMode.RECORDING && (drawableName == "start_record_key" || drawableName == "pause_record_key") -> {
                val svc = recordingService
                when (svc?.state?.value) {
                    RecordingState.IDLE, null -> {
                        startActivity(Intent(this, BubbleTrampolineActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                    RecordingState.RECORDING -> svc.pause()
                    RecordingState.PAUSED -> svc.resume()
                }
                collapseMenu()
            }
            currentMode == BubbleMode.RECORDING && drawableName == "stop_record_key" -> {
                recordingService?.stop()
                collapseMenu()
            }
            currentMode == BubbleMode.RECORDING && drawableName == "music_switch_key" -> {
                currentMode = BubbleMode.MUSIC
                refreshBubbleImage()
                collapseMenu()
                loadPinnedPlaylists()
            }
            currentMode == BubbleMode.MUSIC && drawableName == "record_switch_key" -> {
                currentMode = BubbleMode.RECORDING
                refreshBubbleImage()
                collapseMenu()
                musicService?.stop()
            }
            currentMode == BubbleMode.MUSIC && drawableName == "playlist_slot" -> {
                playPinnedPlaylist(index)
                collapseMenu()
            }
        }
    }

    private fun playPinnedPlaylist(index: Int) {
        val playlist = pinnedPlaylists.getOrNull(index) ?: return
        val intent = Intent(this, MusicPlayerService::class.java)
        tryBindMusicService()
        startForegroundService(intent)
        handler.postDelayed({
            musicService?.playPlaylist(playlist)
        }, 300)
    }

    // ---------- Drag-to-remove ----------

    private fun showRemoveTarget() {
        if (removeTargetView != null) return
        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#CC1A0508"))
            setStroke(3, Color.parseColor("#FF2E4D"))
        }
        val target = FrameLayout(this).apply { background = bg }
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
            180, 180, type,
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
        val left = location[0]; val top = location[1]
        val right = left + target.width; val bottom = top + target.height
        return rawX in left.toFloat()..right.toFloat() && rawY in top.toFloat()..bottom.toFloat()
    }

    private fun updateRemoveTargetHighlight(rawX: Float, rawY: Float) {
        val target = removeTargetView as? FrameLayout ?: return
        val isOver = isOverRemoveTarget(rawX, rawY)
        target.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isOver) Color.parseColor("#FF2E4D") else Color.parseColor("#CC1A0508"))
            setStroke(3, Color.parseColor("#FF2E4D"))
        }
    }

    private fun hideRemoveTarget() {
        removeTargetView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        removeTargetView = null
    }

    // ---------- Timer / badge updates ----------

    private fun updateBubbleAndBadge() {
        if (currentMode != BubbleMode.RECORDING) {
            timestampBadge?.visibility = View.GONE
            return
        }
        val svc = recordingService
        if (svc == null) {
            try {
                bindService(Intent(this, RecordingService::class.java), recordingConnection, 0)
            } catch (_: Exception) { }
            timestampBadge?.visibility = View.GONE
            return
        }
        val state = svc.state.value
        if (lastKnownState != RecordingState.IDLE && state == RecordingState.IDLE && isMenuExpanded) {
            collapseMenu()
        }
        lastKnownState = state

        if (state == RecordingState.IDLE) {
            timestampBadge?.visibility = View.GONE
            return
        }
        // Uses the same pause-aware getElapsedMs() the app screen reads, so
        // the two timers can never drift apart, including across pause/resume.
        val elapsedMs = svc.getElapsedMs()
        val totalSeconds = elapsedMs / 1000
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        timestampBadge?.visibility = View.VISIBLE
        timestampBadge?.text = String.format("%02d:%02d", m, s)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(tickRunnable)
        if (recordingBound) {
            try { unbindService(recordingConnection) } catch (_: Exception) { }
        }
        if (musicBound) {
            try { unbindService(musicConnection) } catch (_: Exception) { }
        }
        collapseMenu()
        hideRemoveTarget()
        bubbleView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        timestampBadge?.let {
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
    }
}
