package com.audioninja.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.audioninja.app.data.FavoritesRepository
import com.audioninja.app.data.Recording
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.ui.components.AppHeaderBar
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(navController: NavController, favoritesOnly: Boolean = false) {
    val context = LocalContext.current
    val repo = remember { RecordingRepository(context) }
    val favoritesRepo = remember { FavoritesRepository(context) }
    val scope = rememberCoroutineScope()

    val favoriteIds by favoritesRepo.favoriteIds.collectAsState(initial = emptySet())

    var recordings by remember { mutableStateOf(repo.listRecordings()) }
    var query by remember { mutableStateOf("") }

    val filtered = recordings.filter {
        it.fileName.contains(query, ignoreCase = true) &&
            (!favoritesOnly || it.id in favoriteIds)
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

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search recordings...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

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
                            isFavorite = recording.id in favoriteIds,
                            onToggleFavorite = {
                                scope.launch { favoritesRepo.toggleFavorite(recording.id) }
                            },
                            onDelete = {
                                repo.delete(recording)
                                recordings = repo.listRecordings()
                            },
                            onClick = {
                                navController.navigate("nowPlaying/${recording.id}")
                            }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
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
                    contentDescription = "Toggle favorite",
                    tint = if (isFavorite) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.1f MB", mb)
}

private fun formatDate(epochMs: Long): String =
    java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US).format(java.util.Date(epochMs))
