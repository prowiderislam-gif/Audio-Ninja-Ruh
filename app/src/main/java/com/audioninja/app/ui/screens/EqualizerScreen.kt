package com.audioninja.app.ui.screens

import android.media.MediaPlayer
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audioninja.app.data.EqualizerRepository
import com.audioninja.app.service.EqualizerEngine
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import kotlinx.coroutines.launch

@Composable
fun EqualizerScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { EqualizerRepository(context) }
    val scope = rememberCoroutineScope()

    val enabled by repo.enabled.collectAsState(initial = false)
    val bassBoost by repo.bassBoost.collectAsState(initial = 0)
    val savedBandLevels by repo.bandLevels.collectAsState(initial = emptyList())

    // A silent, short-lived MediaPlayer purely to query the device's real EQ
    // band count/frequencies/range and preview changes live while adjusting.
    var previewPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var engine by remember { mutableStateOf<EqualizerEngine?>(null) }

    DisposableEffect(Unit) {
        val player = MediaPlayer()
        previewPlayer = player
        val eq = EqualizerEngine(player.audioSessionId)
        engine = eq
        onDispose {
            eq.release()
            player.release()
        }
    }

    var bandLevels by remember(engine) {
        mutableStateOf(
            if (savedBandLevels.size == (engine?.numberOfBands ?: 0)) savedBandLevels
            else List(engine?.numberOfBands ?: 0) { 0 }
        )
    }

    LaunchedEffect(engine, enabled, bassBoost, bandLevels) {
        engine?.setEnabled(enabled)
        engine?.setBassBoostStrength(bassBoost)
        engine?.applyBandLevels(bandLevels)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BrandBanner()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { navController.popBackStack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Equalizer", style = MaterialTheme.typography.titleLarge)
        }

        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Enable Equalizer", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Applies to all recordings and playlists",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { scope.launch { repo.setEnabled(it) } }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("Bass Boost", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Slider(
                value = bassBoost.toFloat(),
                onValueChange = { newVal ->
                    scope.launch { repo.setBassBoost(newVal.toInt()) }
                },
                valueRange = 0f..1000f,
                colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed),
                enabled = enabled
            )
            Text(
                "${(bassBoost / 10)}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val eqEngine = engine
            if (eqEngine != null && eqEngine.numberOfBands > 0) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Frequency Bands", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))

                val minLevel = eqEngine.levelRangeMb.getOrElse(0) { -1500 }
                val maxLevel = eqEngine.levelRangeMb.getOrElse(1) { 1500 }

                bandLevels.forEachIndexed { index, level ->
                    val freqHz = eqEngine.bandFrequencies.getOrNull(index) ?: 0
                    val freqLabel = if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz"

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            freqLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.width(56.dp)
                        )
                        Slider(
                            value = level.toFloat(),
                            onValueChange = { newVal ->
                                val updated = bandLevels.toMutableList()
                                updated[index] = newVal.toInt()
                                bandLevels = updated
                            },
                            onValueChangeFinished = {
                                scope.launch { repo.setBandLevels(bandLevels) }
                            },
                            valueRange = minLevel.toFloat()..maxLevel.toFloat(),
                            colors = SliderDefaults.colors(thumbColor = NeonRed, activeTrackColor = NeonRed),
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "This device doesn't support a custom equalizer — bass boost may still work above.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
