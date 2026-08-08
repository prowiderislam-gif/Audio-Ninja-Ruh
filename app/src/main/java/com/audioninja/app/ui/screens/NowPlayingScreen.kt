package com.audioninja.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.ui.theme.NeonRed
import kotlinx.coroutines.delay

@Composable
fun NowPlayingScreen(recordingId: String, navController: NavController) {
    val context = LocalContext.current
    val repo = remember { RecordingRepository(context) }
    val recording = remember { repo.listRecordings().firstOrNull { it.id == recordingId } }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(recording?.durationMs?.toInt() ?: 0) }

    DisposableEffect(recording?.filePath) {
        val path = recording?.filePath
        val player = if (path != null) {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                durationMs = duration
            }
        } else null
        mediaPlayer = player
        onDispose {
            player?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = mediaPlayer?.currentPosition ?: 0
            delay(500)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.Start)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
        }

        Spacer(modifier = Modifier.height(24.dp))
        Icon(Icons.Filled.MusicNote, contentDescription = null, modifier = Modifier.size(96.dp), tint = NeonRed)
        Spacer(modifier = Modifier.height(16.dp))
        Text(recording?.fileName ?: "Unknown", style = MaterialTheme.typography.titleLarge)
        Text("Audio Recording", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))

        Slider(
            value = positionMs.toFloat().coerceIn(0f, durationMs.toFloat().coerceAtLeast(1f)),
            onValueChange = { newVal ->
                positionMs = newVal.toInt()
                mediaPlayer?.seekTo(positionMs)
            },
            valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f)
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatMs(positionMs))
            Text(formatMs(durationMs))
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { mediaPlayer?.seekTo((positionMs - 10000).coerceAtLeast(0)) }) {
                Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s")
            }
            FilledIconButton(
                onClick = {
                    val player = mediaPlayer ?: return@FilledIconButton
                    if (isPlaying) player.pause() else player.start()
                    isPlaying = !isPlaying
                },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonRed),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
            }
            IconButton(onClick = { mediaPlayer?.seekTo((positionMs + 10000).coerceAtMost(durationMs)) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            IconButton(onClick = { }) {
                Icon(Icons.Filled.FavoriteBorder, contentDescription = "Favorite")
            }
            IconButton(onClick = { }) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = { }) {
                Icon(Icons.Filled.Share, contentDescription = "Share")
            }
            IconButton(onClick = {
                recording?.let { repo.delete(it) }
                navController.popBackStack()
            }) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}
