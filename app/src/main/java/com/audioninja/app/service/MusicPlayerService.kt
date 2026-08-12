package com.audioninja.app.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.audioninja.app.AudioNinjaApp
import com.audioninja.app.MainActivity
import com.audioninja.app.data.Playlist
import com.audioninja.app.data.PlaylistTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class MusicPlaybackState { IDLE, PLAYING, PAUSED }

/**
 * Plays tracks from a Playlist. Shared by the in-app player and the floating
 * bubble's Music Mode so both stay in sync automatically.
 */
class MusicPlayerService : Service() {

    private val binder = LocalBinder()
    private var mediaPlayer: MediaPlayer? = null

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

    inner class LocalBinder : Binder() {
        fun getService(): MusicPlayerService = this@MusicPlayerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

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
        startForegroundNotification(track.title)
    }

    private fun startForegroundNotification(trackTitle: String) {
        val openIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, AudioNinjaApp.RECORDING_CHANNEL_ID)
            .setContentTitle("Audio Ninja — Music Mode")
            .setContentText(trackTitle)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
        startForeground(2, notification)
    }

    private fun updateNotification() {
        val track = _currentTrack.value ?: return
        startForegroundNotification(track.title)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            mediaPlayer?.release()
        } catch (_: Exception) { }
        mediaPlayer = null
    }
}
