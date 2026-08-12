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
import androidx.navigation.NavController
import com.audioninja.app.data.Playlist
import com.audioninja.app.data.PlaylistRepository
import com.audioninja.app.ui.components.AppHeaderBar
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun PlaylistsScreen(navController: NavController) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val scope = rememberCoroutineScope()

    val playlists by repo.playlists.collectAsState(initial = emptyList())
    val pinnedIds by repo.pinnedPlaylistIds.collectAsState(initial = emptyList())

    var showCreateDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        BrandBanner()
        AppHeaderBar()

        Column(modifier = Modifier.weight(1f).padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                FilledTonalButton(
                    onClick = { showCreateDialog = true },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonRed)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Pick up to 3 playlists (${pinnedIds.size}/3) to make available in the floating bubble's Music Mode.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No playlists yet — tap New to create one",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        val isPinned = playlist.id in pinnedIds
                        PlaylistRow(
                            playlist = playlist,
                            isPinned = isPinned,
                            onClick = { navController.navigate("playlistDetail/${playlist.id}") },
                            onTogglePin = {
                                scope.launch {
                                    val newPins = if (isPinned) {
                                        pinnedIds - playlist.id
                                    } else {
                                        if (pinnedIds.size >= 3) pinnedIds else pinnedIds + playlist.id
                                    }
                                    repo.setPinnedPlaylistIds(newPins)
                                }
                            },
                            onRename = { renameTarget = playlist },
                            onDelete = { scope.launch { repo.deletePlaylist(playlist.id) } }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(24.dp)) }
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    placeholder = { Text("Playlist name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { repo.createPlaylist(name.trim()) }
                    }
                    showCreateDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget?.let { target ->
        var name by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Playlist") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        scope.launch { repo.renamePlaylist(target.id, name.trim()) }
                    }
                    renameTarget = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.QueueMusic, contentDescription = null, tint = NeonRed, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "${playlist.tracks.size} tracks" + if (isPinned) " • In bubble" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isPinned) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onTogglePin) {
                Icon(
                    if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Toggle bubble playlist",
                    tint = if (isPinned) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRename) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
    }
}
