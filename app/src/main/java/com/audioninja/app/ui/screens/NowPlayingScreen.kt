package com.audioninja.app.ui.screens

import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audioninja.app.data.FavoritesRepository
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurface
import com.audioninja.app.ui.theme.NinjaSurfaceElevated
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NowPlayingScreen(recordingId: String, navController: NavController) {
    val context = LocalContext.current
    val repo = remember { RecordingRepository(context) }
    val favoritesRepo = remember { FavoritesRepository(context) }
    val scope = rememberCoroutineScope()

    val favoriteIds by favoritesRepo.favoriteIds.collectAsState(initial = emptySet())
    val isFavorite = recordingId in favoriteIds

    var recording by remember { mutableStateOf<com.audioninja.app.data.Recording?>(null) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }

    LaunchedEffect(recordingId) {
        recording = repo.listRecordings().firstOrNull { it.id == recordingId }
    }

    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0) }
    var durationMs by remember { mutableStateOf(0) }

    DisposableEffect(recording?.filePath) {
        val path = recording?.filePath
        val player = if (path != null) {
            MediaPlayer().apply {
                setDataSource(path)
                prepare()
                durationMs = duration
                setOnCompletionListener {
                    isPlaying = false
                    positionMs = 0
                    seekTo(0)
                }
                start()
                isPlaying = true
            }
        } else null
        mediaPlayer = player
        onDispose {
            player?.stop()
            player?.release()
            mediaPlayer = null
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            positionMs = mediaPlayer?.currentPosition ?: 0
            delay(200)
        }
    }

    // Live waveform captured from actual playback via Visualizer; falls back to a
    // flat line if the device/API doesn't support it.
    var waveformBars by remember { mutableStateOf(FloatArray(40) { 0.08f }) }

    DisposableEffect(mediaPlayer, isPlaying) {
        var visualizer: Visualizer? = null
        val player = mediaPlayer
        if (player != null && isPlaying) {
            try {
                visualizer = Visualizer(player.audioSessionId).apply {
                    val captureSize = Visualizer.getCaptureSizeRange()[1]
                    setCaptureSize(captureSize)
                    setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                            if (waveform != null && waveform.isNotEmpty()) {
                                val bars = 40
                                val step = (waveform.size / bars).coerceAtLeast(1)
                                val newData = FloatArray(bars)
                                for (i in 0 until bars) {
                                    val idx = (i * step).coerceIn(0, waveform.size - 1)
                                    val raw = (waveform[idx].toInt() and 0xFF) - 128
                                    newData[i] = (kotlin.math.abs(raw) / 128f).coerceIn(0.06f, 1f)
                                }
                                waveformBars = newData
                            }
                        }
                        override fun onFftDataCapture(v: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
                    }, Visualizer.getMaxCaptureRate() / 2, true, false)
                    enabled = true
                }
            } catch (_: Exception) {
                // Visualizer unsupported on this device/API — bars stay at their fallback values.
            }
        }
        onDispose {
            try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) { }
        }
    }

    // Disc rotation: advances only while playing, resets to 0 the instant playback
    // pauses or finishes (not merely frozen mid-spin).
    var rotationDeg by remember { mutableStateOf(0f) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            var last = System.nanoTime()
            while (isPlaying) {
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (dragAmount > 12) {
                        change.consume()
                        mediaPlayer?.pause()
                        isPlaying = false
                        navController.popBackStack()
                    }
                }
            }
    ) {
        BrandBanner()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                mediaPlayer?.pause()
                isPlaying = false
                navController.popBackStack()
            }) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Back")
            }
            Spacer(modifier = Modifier.weight(1f))
            Text("AUDIO NINJA PLAYER", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = {
                mediaPlayer?.pause()
                isPlaying = false
                navController.popBackStack()
            }) {
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
            if (logoResId != 0) {
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
            } else {
                Icon(
                    Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = NeonRed,
                    modifier = Modifier.size(72.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            recording?.fileName ?: "Unknown",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            "${recording?.format ?: ""} • ${formatSize(recording?.sizeBytes ?: 0)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
        WaveformScrubber(
            bars = waveformBars,
            progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
            onSeek = { fraction ->
                val newPos = (fraction * durationMs).toInt().coerceIn(0, durationMs)
                positionMs = newPos
                mediaPlayer?.seekTo(newPos)
            }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatMs(positionMs), style = MaterialTheme.typography.labelMedium)
            Text(formatMs(durationMs), style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(12.dp))

        SpeedSelector(
            selected = playbackSpeed,
            onSelect = { speed ->
                playbackSpeed = speed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        mediaPlayer?.let { player ->
                            val wasPlaying = player.isPlaying
                            player.playbackParams = player.playbackParams.setSpeed(speed)
                            if (wasPlaying && !player.isPlaying) player.start()
                        }
                    } catch (_: Exception) { }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { /* previous recording: future addition */ }) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous")
            }
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
                modifier = Modifier.size(72.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                    modifier = Modifier.size(32.dp)
                )
            }
            IconButton(onClick = { mediaPlayer?.seekTo((positionMs + 10000).coerceAtMost(durationMs)) }) {
                Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s")
            }
            IconButton(onClick = { /* next recording: future addition */ }) {
                Icon(Icons.Filled.SkipNext, contentDescription = "Next")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ActionButton(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = "Favorite",
                tint = if (isFavorite) NeonRed else null,
                onClick = {
                    scope.launch { favoritesRepo.toggleFavorite(recordingId) }
                }
            )
            ActionButton(icon = Icons.Filled.Edit, label = "Rename", onClick = { })
            ActionButton(icon = Icons.Filled.Share, label = "Share", onClick = { })
            ActionButton(
                icon = Icons.Filled.Delete,
                label = "Delete",
                onClick = {
                    scope.launch {
                        recording?.let { repo.delete(it) }
                        navController.popBackStack()
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: androidx.compose.ui.graphics.Color? = null,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = NinjaSurfaceElevated)
        ) {
            Icon(icon, contentDescription = label, tint = tint ?: NeonRed)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun SpeedSelector(selected: Float, onSelect: (Float) -> Unit) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .background(NinjaSurfaceElevated, RoundedCornerShape(20.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        speeds.forEach { speed ->
            val isSelected = speed == selected
            val label = if (speed == 1.0f) "1x" else "${speed}x"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onSelect(speed) }
                    .background(if (isSelected) NeonRed else Color.Transparent)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(label, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun WaveformScrubber(bars: FloatArray, progress: Float, onSeek: (Float) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .padding(horizontal = 20.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        bars.forEachIndexed { index, amplitude ->
            val isPast = index.toFloat() / bars.size < progress
            val heightDp = (10 + amplitude * 38).dp
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(heightDp)
                    .background(
                        if (isPast) NeonRed else NinjaSurfaceElevated,
                        RoundedCornerShape(2.dp)
                    )
            )
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
