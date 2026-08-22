package com.audioninja.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.audioninja.app.AudioNinjaApp
import com.audioninja.app.MainActivity
import com.audioninja.app.data.Playlist
import com.audioninja.app.data.PlaylistTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MusicPlaybackState { IDLE, PLAYING, PAUSED }

class MusicPlayerService : Service() {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var mediaSession: MediaSessionCompat

    private var currentPlaylist: Playlist? = null
    private var queue: List<PlaylistTrack> = emptyList()
    private var currentIndex: Int = -1
    private var shuffle: Boolean = false
    private var loop: Boolean = false

    private val _state = MutableStateFlow(MusicPlaybackState.IDLE)
    val state: StateFlow<MusicPlaybackState> = _state

    private val _currentTrack = MutableStateFlow<PlaylistTrack?>(null)
    val currentTrack: StateFlow<PlaylistTrack?> = _currentTrack

    private val _currentPlaylistName = MutableStateFlow<String?>(null)
    val currentPlaylistName: StateFlow<String?> = _currentPlaylistName

    private val handler = Handler(Looper.getMainLooper())
    private val positionTickRunnable = object : Runnable {
        override fun run() {
            if (mediaPlayer?.isPlaying == true) {
                updatePlaybackState()
            }
            handler.postDelayed(this, 1000)
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                ACTION_PLAY_PAUSE -> playPause()
                ACTION_NEXT -> next()
                ACTION_PREVIOUS -> previous()
                ACTION_STOP -> stop()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        mediaSession = MediaSessionCompat(this, "AudioNinjaMusicSession").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() { playPause() }
                override fun onPause() { playPause() }
                override fun onSkipToNext() { next() }
                override fun onSkipToPrevious() { previous() }
                override fun onStop() { stop() }
                override fun onSeekTo(pos: Long) { seekTo(pos.toInt()) }
            })
            isActive = true
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_PLAY_PAUSE)
            addAction(ACTION_NEXT)
            addAction(ACTION_PREVIOUS)
            addAction(ACTION_STOP)
        }
        ContextCompat.registerReceiver(this, controlReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        handler.post(positionTickRunnable)
    }

    fun playPlaylist(playlist: Playlist, startTrackId: String? = null) {
        if (playlist.tracks.isEmpty()) return
        currentPlaylist = playlist
        shuffle = playlist.shuffle
        loop = playlist.loop
        queue = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        currentIndex = startTrackId?.let { id -> queue.indexOfFirst { it.id == id } }?.takeIf { it >= 0 } ?: 0
        _currentPlaylistName.value = playlist.name
        playCurrentIndex()
    }

    fun playPause() {
        val player = mediaPlayer ?: return
        if (player.isPlaying) {
            player.pause()
            _state.value = MusicPlaybackState.PAUSED
        } else {
            player.start()
            _state.value = MusicPlaybackState.PLAYING
        }
        updatePlaybackState()
        updateNotification()
    }

    fun next() {
        if (queue.isEmpty()) return
        currentIndex = if (currentIndex + 1 < queue.size) currentIndex + 1 else if (loop) 0 else return
        playCurrentIndex()
    }

    fun previous() {
        if (queue.isEmpty()) return
        currentIndex = if (currentIndex - 1 >= 0) currentIndex - 1 else if (loop) queue.size - 1 else 0
        playCurrentIndex()
    }

    fun seekTo(ms: Int) {
        mediaPlayer?.seekTo(ms)
        updatePlaybackState()
    }

    fun getCurrentPositionMs(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDurationMs(): Int = mediaPlayer?.duration ?: 0

    fun setShuffle(value: Boolean) {
        shuffle = value
        val current = queue.getOrNull(currentIndex)
        queue = if (shuffle) queue.shuffled() else (currentPlaylist?.tracks ?: queue)
        currentIndex = current?.let { t -> queue.indexOfFirst { it.id == t.id } }?.coerceAtLeast(0) ?: 0
    }

    fun setLoop(value: Boolean) {
        loop = value
    }

    fun stop() {
        handler.removeCallbacks(positionTickRunnable)
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        _state.value = MusicPlaybackState.IDLE
        _currentTrack.value = null
        _currentPlaylistName.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun playCurrentIndex() {
        val track = queue.getOrNull(currentIndex) ?: return
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }

        mediaPlayer = MediaPlayer().apply {
            try {
                if (track.isInternal) {
                    setDataSource(track.uri)
                } else {
                    setDataSource(this@MusicPlayerService, Uri.parse(track.uri))
                }
                prepare()
                start()
                setOnCompletionListener { next() }
            } catch (_: Exception) {
                return@apply
            }
        }
        _currentTrack.value = track
        _state.value = MusicPlaybackState.PLAYING
        updateMetadata(track)
        updatePlaybackState()
        startForegroundNotification(track.title)
    }

    /** Loads the correct artwork: the track's own cover if it has one, else the app logo. */
    private fun loadArtworkBitmap(track: PlaylistTrack): Bitmap? {
        return try {
            if (track.coverPath != null) {
                BitmapFactory.decodeFile(track.coverPath)
            } else {
                val resId = resources.getIdentifier("logo", "drawable", packageName)
                if (resId != 0) BitmapFactory.decodeResource(resources, resId) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateMetadata(track: PlaylistTrack) {
        val artwork = loadArtworkBitmap(track)
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentPlaylistName.value ?: "Audio Ninja")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, getDurationMs().toLong())
        if (artwork != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
        }
        mediaSession.setMetadata(builder.build())
    }

    private fun updatePlaybackState() {
        val isPlaying = mediaPlayer?.isPlaying == true
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PAUSE or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP or
                    PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                getCurrentPositionMs().toLong(),
                1f
            )
        mediaSession.setPlaybackState(stateBuilder.build())
    }

    private fun broadcastPendingIntent(action: String): PendingIntent {
        val intent = Intent(action).setPackage(packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(this, action.hashCode(), intent, flags)
    }

    private fun startForegroundNotification(trackTitle: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val contentPendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val isPlaying = mediaPlayer?.isPlaying == true
        val playPauseIcon = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
        val track = _currentTrack.value
        val largeIcon = track?.let { loadArtworkBitmap(it) }

        val notification = NotificationCompat.Builder(this, AudioNinjaApp.RECORDING_CHANNEL_ID)
            .setContentTitle(trackTitle)
            .setContentText(currentPlaylistName.value ?: "Audio Ninja")
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
            .setContentIntent(contentPendingIntent)
            .setOngoing(false)
            .setDeleteIntent(broadcastPendingIntent(ACTION_STOP))
            .addAction(android.R.drawable.ic_media_previous, "Previous", broadcastPendingIntent(ACTION_PREVIOUS))
            .addAction(playPauseIcon, "Play/Pause", broadcastPendingIntent(ACTION_PLAY_PAUSE))
            .addAction(android.R.drawable.ic_media_next, "Next", broadcastPendingIntent(ACTION_NEXT))
            .setStyle(
                MediaNotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()
        startForeground(2, notification)
    }

    private fun updateNotification() {
        val track = _currentTrack.value ?: return
        startForegroundNotification(track.title)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(positionTickRunnable)
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
        try { mediaSession.release() } catch (_: Exception) { }
        try { unregisterReceiver(controlReceiver) } catch (_: Exception) { }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.audioninja.app.MUSIC_PLAY_PAUSE"
        const val ACTION_NEXT = "com.audioninja.app.MUSIC_NEXT"
        const val ACTION_PREVIOUS = "com.audioninja.app.MUSIC_PREVIOUS"
        const val ACTION_STOP = "com.audioninja.app.MUSIC_STOP"
    }
}
