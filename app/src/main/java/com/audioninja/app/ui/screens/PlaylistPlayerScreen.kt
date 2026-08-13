package com.audioninja.app.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.audioninja.app.data.PlaylistRepository
import com.audioninja.app.data.PlaylistTrack
import com.audioninja.app.service.MusicPlaybackState
import com.audioninja.app.service.MusicPlayerService
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun PlaylistPlayerScreen(playlistId: String, trackId: String, navController: NavController) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()
    val playlists by repo.playlists.collectAsState(initial = emptyList())
    val playlist = playlists.firstOrNull { it.id == playlistId }

    var service by remember { mutableStateOf<MusicPlayerService?>(null) }
    var bound by remember { mutableStateOf(false) }

    val connection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = (binder as MusicPlayerService.LocalBinder).getService()
                bound = true
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                bound = false
                service = null
            }
        }
    }

    DisposableEffect(Unit) {
        val intent = Intent(context, MusicPlayerService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        context.startForegroundService(intent)
        onDispose {
            if (bound) {
                try { context.unbindService(connection) } catch (_: Exception) { }
            }
        }
    }

    var hasStartedPlayback by remember { mutableStateOf(false) }
    LaunchedEffect(service, playlist) {
        if (service != null && playlist != null && !hasStartedPlayback) {
            service?.playPlaylist(playlist, trackId)
            hasStartedPlayback = true
        }
    }

    val playbackState by (service?.state ?: remember { MutableStateFlow(MusicPlaybackState.IDLE) }).collectAsState()
    val currentTrack by (service?.currentTrack ?: remember { MutableStateFlow<PlaylistTrack?>(null) }).collectAsState()

    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }

    LaunchedEffect(playbackState) {
        while (playbackState == MusicPlaybackState.PLAYING) {
            positionMs = service?.getCurrentPositionMs() ?: 0
            durationMs = service?.getDurationMs() ?: 0
            delay(300)
        }
    }

    var rotationDeg by remember { mutableStateOf(0f) }
    LaunchedEffect(playbackState) {
        if (playbackState == MusicPlaybackState.PLAYING) {
            var last = System.nanoTime()
            while (playbackState == MusicPlaybackState.PLAYING) {
                val now = System.nanoTime()
                val deltaSec = (now - last) / 1_000_000_000f
                last = now
                rotationDeg = (rotationDeg + deltaSec * 45f) % 360f
                delay(16)
            }
        } else {
            rotationDeg = 0f
        }
    }

    val logoResId = remember {
        context.resources.getIdentifier("logo", "drawable", context.packageName)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrandBanner()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(playlist?.name ?: "Playlist", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.Close, contentDescription = "Close")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .padding(horizontal = 40.dp)
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(2.dp, NeonRed, CircleShape)
                .background(NinjaSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            val track = currentTrack
            if (track?.coverPath != null) {
                AsyncImage(
                    model = track.coverPath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.tint(Color(0x33FF2E4D), BlendMode.Overlay),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape)
                        .rotate(rotationDeg)
                )
            } else if (logoResId != 0) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Audio Ninja logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                        .clip(CircleShape)
                        .rotate(rotationDeg)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            currentTrack?.title ?: "Loading...",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { service?.previous() }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
            FilledIconButton(
                onClick = { service?.playPause() },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonRed),
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (playbackState == MusicPlaybackState.PLAYING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { service?.next() }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val shuffleOn = playlist?.shuffle == true
            val loopOn = playlist?.loop == true
            IconButton(onClick = {
                val pl = playlist ?: return@IconButton
                val newValue = !pl.shuffle
                scope.launch { repo.setShuffle(pl.id, newValue) }
                service?.setShuffle(newValue)
            }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (shuffleOn) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                val pl = playlist ?: return@IconButton
                val newValue = !pl.loop
                scope.launch { repo.setLoop(pl.id, newValue) }
                service?.setLoop(newValue)
            }) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = "Loop",
                    tint = if (loopOn) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
