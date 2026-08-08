package com.audioninja.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.audioninja.app.data.Recording
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.data.SettingsRepository
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

    val filtered = recordings.filter {
        it.fileName.contains(query, ignoreCase = true) &&
            (!favoritesOnly || favorites[it.id] == true)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = if (favoritesOnly) "Favorites" else "Recordings Library",
            style = MaterialTheme.typography.headlineSmall
        )
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search by name") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (favoritesOnly) "No favorites yet" else "No recordings yet — tap Record to get started",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { recording ->
                    RecordingRow(
                        recording = recording,
                        isFavorite = favorites[recording.id] == true,
                        onToggleFavorite = { favorites[recording.id] = favorites[recording.id] != true },
                        onClick = { navController.navigate("nowPlaying/${recording.id}") }
                    )
                }
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: Recording,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(recording.fileName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${formatDuration(recording.durationMs)} • ${formatDate(recording.dateCreated)}",
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
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val m = totalSeconds / 60
    val s = totalSeconds % 60
    return String.format("%02d:%02d", m, s)
}

private fun formatDate(epochMs: Long): String =
    SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(epochMs))
