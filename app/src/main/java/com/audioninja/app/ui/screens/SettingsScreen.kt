package com.audioninja.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.audioninja.app.StoragePermissionActivity
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.data.SettingsRepository
import com.audioninja.app.service.FloatingBubbleService
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository(context) }
    val recordingRepo = remember { RecordingRepository(context) }
    val scope = rememberCoroutineScope()

    val outputFormat by repo.outputFormat.collectAsState(initial = "AAC (M4A)")
    val sampleRate by repo.sampleRate.collectAsState(initial = 48000)
    val bitrate by repo.bitrate.collectAsState(initial = 320000)
    val recordMicWithInternal by repo.recordMicWithInternal.collectAsState(initial = false)
    val autoKeepBackground by repo.autoKeepBackground.collectAsState(initial = true)
    val floatingBubbleEnabled by repo.floatingBubbleEnabled.collectAsState(initial = false)

    var showOverlayPermissionNote by remember { mutableStateOf(false) }
    var storageGranted by remember { mutableStateOf(recordingRepo.canWriteToPublicStorage()) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BrandBanner()

        Column(modifier = Modifier.padding(16.dp)) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            Text("Recording Format", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsDropdownRow(
                label = "Output Format",
                value = outputFormat,
                options = listOf("AAC (M4A)", "WAV", "MP3"),
                onSelect = { scope.launch { repo.setOutputFormat(it) } }
            )
            SettingsDropdownRow(
                label = "Sample Rate",
                value = "$sampleRate Hz",
                options = listOf("44100", "48000", "96000"),
                onSelect = { scope.launch { repo.setSampleRate(it.toInt()) } },
                displayTransform = { "$it Hz" }
            )
            SettingsDropdownRow(
                label = "Bitrate",
                value = "${bitrate / 1000} kbps",
                options = listOf("128000", "192000", "256000", "320000"),
                onSelect = { scope.launch { repo.setBitrate(it.toInt()) } },
                displayTransform = { "${it.toInt() / 1000} kbps" }
            )

            SettingsSwitchRow(
                label = "Record Microphone Audio",
                sublabel = "Layer your mic over internal audio (off = internal audio only)",
                checked = recordMicWithInternal,
                onCheckedChange = { scope.launch { repo.setRecordMicWithInternal(it) } }
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Storage", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                "Current location: ${recordingRepo.storagePathDisplay(true)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            if (!storageGranted) {
                Button(
                    onClick = {
                        context.startActivity(Intent(context, StoragePermissionActivity::class.java))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Grant Storage Access (saves to Download/Audio Ninja)")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "After allowing access in Settings, come back here — the app will detect it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "✓ Storage access granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = NeonRed
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Floating Bubble", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchRow(
                label = "Show Floating Bubble",
                sublabel = "A draggable control with a live timer while recording",
                checked = floatingBubbleEnabled,
                onCheckedChange = { enabled ->
                    if (enabled) {
                        if (Settings.canDrawOverlays(context)) {
                            scope.launch { repo.setFloatingBubbleEnabled(true) }
                            context.startService(Intent(context, FloatingBubbleService::class.java))
                        } else {
                            showOverlayPermissionNote = true
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        }
                    } else {
                        scope.launch { repo.setFloatingBubbleEnabled(false) }
                        context.stopService(Intent(context, FloatingBubbleService::class.java))
                    }
                }
            )
            if (showOverlayPermissionNote) {
                Text(
                    "Allow \"Display over other apps\" for Audio Ninja, then come back and turn this on again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Advanced", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            SettingsSwitchRow(
                label = "Auto Keep",
                sublabel = "Keep recording for background",
                checked = autoKeepBackground,
                onCheckedChange = { scope.launch { repo.setAutoKeepBackground(it) } }
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    label: String,
    sublabel: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            sublabel?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsDropdownRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    displayTransform: (String) -> String = { it }
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { expanded = true }) { Text(value) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(displayTransform(option)) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
