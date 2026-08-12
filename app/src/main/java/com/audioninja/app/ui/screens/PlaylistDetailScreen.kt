package com.audioninja.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.audioninja.app.data.Playlist
import com.audioninja.app.data.PlaylistRepository
import com.audioninja.app.data.PlaylistTrack
import com.audioninja.app.data.RecordingRepository
import com.audioninja.app.ui.components.AppHeaderBar
import com.audioninja.app.ui.components.BrandBanner
import com.audioninja.app.ui.theme.NeonRed
import com.audioninja.app.ui.theme.NinjaSurfaceElevated
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailScreen(playlistId: String, navController: NavController) {
    val context = LocalContext.current
    val repo = remember { PlaylistRepository(context) }
    val recordingRepo = remember { RecordingRepository(context) }
    val scope = rememberCoroutineScope()

    val playlists by repo.playlists.collectAsState(initial = emptyList())
    val playlist = playlists.firstOrNull { it.id == playlistId }

    var showAddDialog by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Imported track"
            scope.launch { repo.addImportedTrack(playlistId, uri, name) }
        }
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
            Text(
                playlist?.name ?: "Playlist",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { importLauncher.launch("audio/*") },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonRed),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Audio")
            }
            FilledTonalButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Recording")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val tracks = playlist?.tracks ?: emptyList()
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No tracks yet — import audio or add a recording",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    TrackRow(
                        track = track,
                        onClick = { navController.navigate("playlistPlayer/$playlistId/${track.id}") },
                        onRemove = { scope.launch { repo.removeTrack(playlistId, track.id) } }
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (showAddDialog) {
        val recordings = remember { recordingRepo.listRecordings() }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Recording") },
            text = {
                if (recordings.isEmpty()) {
                    Text("No recordings available yet.")
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(recordings) { rec ->
                            Text(
                                rec.fileName,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch { repo.addInternalRecording(playlistId, rec) }
                                        showAddDialog = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun TrackRow(track: PlaylistTrack, onClick: () -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val logoResId = remember {
        context.resources.getIdentifier("logo", "drawable", context.packageName)
    }

    ElevatedCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (track.coverPath != null) {
                AsyncImage(
                    model = track.coverPath,
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            } else if (logoResId != 0) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = logoResId),
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(CircleShape)
                )
            } else {
                Box(
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(NinjaSurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = NeonRed)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (track.isInternal) "Internal recording" else "Imported",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Remove from playlist")
            }
        }
    }
}

private fun Modifier.clickable(onClick: () -> Unit): Modifier =
    androidx.compose.foundation.clickable(this, onClick = onClick)
