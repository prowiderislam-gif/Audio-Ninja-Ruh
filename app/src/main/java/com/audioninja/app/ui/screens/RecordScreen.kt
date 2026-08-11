package com.audioninja.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.audioninja.app.ui.components.AppHeaderBar
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.service.RecordingState
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurfaceElevated

@Composable
fun RecordScreen(navController: NavController? = null, viewModel: RecordViewModel = viewModel()) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val error by viewModel.error.collectAsState()

    val backgroundResId = remember {
        context.resources.getIdentifier("record_background", "drawable", context.packageName)
    }

    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasMicPermission = granted }

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            viewModel.startInternalRecording(result.resultCode, result.data!!)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (backgroundResId != 0) {
            Image(
                painter = painterResource(id = backgroundResId),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            BrandBanner()
            AppHeaderBar()

            Spacer(modifier = Modifier.height(16.dp))

            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Text(
                    "CURRENT SESSION",
                    style = MaterialTheme.typography.labelSmall,
                    color = NeonRed,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    if (state == RecordingState.IDLE) "Ready for a new capture" else "Internal Capture",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = formatElapsed(elapsed),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            if (state != RecordingState.IDLE) {
                Text(
                    if (state == RecordingState.RECORDING) "Recording..." else "Paused",
                    color = NeonRed,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (state == RecordingState.RECORDING) {
                WaveformPlaceholder()
                Spacer(modifier = Modifier.height(24.dp))
            } else {
                Spacer(modifier = Modifier.height(48.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TransportButton(
                    icon = Icons.Filled.Stop,
                    enabled = state != RecordingState.IDLE,
                    onClick = { viewModel.stop() }
                )

                Spacer(modifier = Modifier.width(24.dp))

                FilledIconButton(
                    onClick = {
                        viewModel.clearError()
                        when (state) {
                            RecordingState.IDLE -> {
                                if (!hasMicPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    captureLauncher.launch(viewModel.getScreenCaptureIntent())
                                } else {
                                    viewModel.startMicRecording()
                                }
                            }
                            else -> { /* center button only starts; use side buttons to stop/pause */ }
                        }
                    },
                    modifier = Modifier.size(88.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = NeonRed)
                ) {
                    Icon(
                        if (state == RecordingState.IDLE) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                        contentDescription = "Record",
                        modifier = Modifier.size(34.dp)
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                TransportButton(
                    icon = if (state == RecordingState.PAUSED) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    enabled = state != RecordingState.IDLE,
                    onClick = {
                        if (state == RecordingState.RECORDING) viewModel.pause() else if (state == RecordingState.PAUSED) viewModel.resume()
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (state == RecordingState.IDLE && error == null) {
                Text(
                    if (!hasMicPermission)
                        "Tap record to allow microphone access (required by Android even for internal-only capture), then confirm the screen-recording permission that follows."
                    else
                        "Records internal audio (media, video, games). A system permission dialog will appear each time you start — this is required by Android.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    error ?: "",
                    color = NeonRed,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TransportButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(56.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = NinjaSurfaceElevated,
            disabledContainerColor = NinjaSurfaceElevated
        )
    ) {
        Icon(icon, contentDescription = null, tint = NeonRed)
    }
}

@Composable
private fun WaveformPlaceholder() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 20.dp)
    ) {
        val heights = listOf(12, 24, 40, 18, 32, 46, 20, 28, 14, 36, 22, 44, 16, 30, 42, 20)
        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(h.dp)
                    .background(NeonRed, shape = RoundedCornerShape(2.dp))
            )
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d:%02d", h, m, s)
}
