package com.audioninja.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.audioninja.app.service.RecordingState
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurface

@Composable
fun RecordScreen(viewModel: RecordViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(220.dp)
                .border(2.dp, NeonRed, CircleShape)
                .background(NinjaSurface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = formatElapsed(elapsed),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (state != RecordingState.IDLE) {
                    Text(
                        text = if (state == RecordingState.RECORDING) "Recording..." else "Paused",
                        color = NeonRed,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (state == RecordingState.RECORDING) {
            WaveformPlaceholder()
            Spacer(modifier = Modifier.height(24.dp))
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (state != RecordingState.IDLE) {
                FilledIconButton(
                    onClick = { if (state == RecordingState.RECORDING) viewModel.pause() else viewModel.resume() },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        if (state == RecordingState.RECORDING) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = "Pause or resume"
                    )
                }
            }

            FilledIconButton(
                onClick = {
                    when (state) {
                        RecordingState.IDLE -> {
                            if (hasMicPermission) {
                                viewModel.startMicRecording()
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        else -> viewModel.stop()
                    }
                },
                modifier = Modifier.size(80.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonRed)
            ) {
                Icon(
                    if (state == RecordingState.IDLE) Icons.Filled.PlayArrow else Icons.Filled.Stop,
                    contentDescription = if (state == RecordingState.IDLE) "Start recording" else "Stop recording",
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        if (!hasMicPermission && state == RecordingState.IDLE) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Microphone permission is needed to record.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WaveformPlaceholder() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(48.dp)
    ) {
        val heights = listOf(12, 24, 40, 18, 32, 46, 20, 28, 14, 36, 22, 44)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(h.dp)
                    .background(NeonRed, shape = androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
