package com.audioninja.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.audioninja.app.data.Recording
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.data.SettingsRepository
import com.audioninja.app.ui.components.AppHeaderBar
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurfaceElevated
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LibraryScreen(navController: NavController, favoritesOnly: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { RecordingRepository(context) }
    val settingsRepo = remember { SettingsRepository(context) }
    val saveToExternal by settingsRepo.saveToExternal.collectAsState(initial = false)

    var recordings by remember(saveToExternal) { mutableStateOf(repo.listRecordings(saveToExternal)) }
    var query by remember { mutableStateOf("") }
    val favorites = remember { mutableStateMapOf<String, Boolean>() }

    var nowPlaying by remember { mutableStateOf<Recording?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }

    DisposableEffect(nowPlaying?.filePath) {
        val path = nowPlaying?.filePath
        val player = if (path != null) {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                durationMs = duration
                start()
                isPlaying = true
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

    val filtered = recordings.filter {
        it.fileName.contains(query, ignoreCase = true) &&
            (!favoritesOnly || favorites[it.id] == true)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrandBanner()
        AppHeaderBar()

        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Text(
                "AUDIO VAULT",
                style = MaterialTheme.typography.labelSmall,
                color = NeonRed,
                letterSpacing = 1.5.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (favoritesOnly) "Favorites" else "Recordings Library",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search recordings...") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (!favoritesOnly) {
                    Spacer(modifier = Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = { /* folder creation: future addition */ },
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonRed)
                    ) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("New Folder")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (favoritesOnly) "No favorites yet" else "No recordings yet — tap Record to get started",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(filtered, key = { it.id }) { recording ->
                        RecordingRow(
                            recording = recording,
                            isFavorite = favorites[recording.id] == true,
                            onToggleFavorite = { favorites[recording.id] = favorites[recording.id] != true },
                            onDelete = {
                                repo.delete(recording)
                                recordings = repo.listRecordings(saveToExternal)
                            },
                            onClick = {
                                if (nowPlaying?.id == recording.id) {
                                    navController.navigate("nowPlaying/${recording.id}")
                                } else {
                                    nowPlaying = recording
                                }
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(90.dp)) }
                }
            }
        }
    }

    nowPlaying?.let { recording ->
        MiniPlayerBar(
            recording = recording,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            onPlayPause = {
                val player = mediaPlayer ?: return@MiniPlayerBar
                if (isPlaying) player.pause() else player.start()
                isPlaying = !isPlaying
            },
            onRewind = { mediaPlayer?.seekTo((positionMs - 10000).coerceAtLeast(0)) },
            onForward = { mediaPlayer?.seekTo((positionMs + 10000).coerceAtMost(durationMs)) },
            onExpand = { navController.navigate("nowPlaying/${recording.id}") },
            onClose = {
                isPlaying = false
                nowPlaying = null
            }
        )
    }
}

@Composable
private fun MiniPlayerBar(
    recording: Recording,
    isPlaying: Boolean,
    positionMs: Int,
    durationMs: Int,
    onPlayPause: () -> Unit,
    onRewind: () -> Unit,
    onForward: () -> Unit,
    onExpand: () -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NinjaSurfaceElevated, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = NeonRed)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    "${formatMs(positionMs)} / ${formatMs(durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onRewind) { Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s") }
            FilledIconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonRed),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
            }
            IconButton(onClick = onForward) { Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s") }
            IconButton(onClick = onExpand) { Icon(Icons.Filled.OpenInFull, contentDescription = "Expand") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close") }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = NeonRed, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.fileName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${recording.format} • ${formatSize(recording.sizeBytes)} • ${formatDate(recording.dateCreated)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = "Toggle favorite"
                )
            }
            IconButton(onClick = onDelete) {
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

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMs))
