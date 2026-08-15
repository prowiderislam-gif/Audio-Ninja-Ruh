package com.audioninja.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.audioninja.app.data.DeviceAudioTrack
import com.audioninja.app.data.DeviceMusicRepository
import com.audioninja.app.data.PlaylistRepository
import com.audioninja.app.data.PlaylistTrack
import com.audioninja.app.data.RecordingRepository
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

    var showDeviceMusicPicker by remember { mutableStateOf(false) }
    var showAddRecordingDialog by remember { mutableStateOf(false) }

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
            IconButton(onClick = {
                val pl = playlist ?: return@IconButton
                scope.launch { repo.setShuffle(pl.id, !pl.shuffle) }
            }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (playlist?.shuffle == true) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = {
                val pl = playlist ?: return@IconButton
                scope.launch { repo.setLoop(pl.id, !pl.loop) }
            }) {
                Icon(
                    Icons.Filled.Repeat,
                    contentDescription = "Loop",
                    tint = if (playlist?.loop == true) NeonRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FilledTonalButton(
                onClick = { showDeviceMusicPicker = true },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = NeonRed),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Music")
            }
            FilledTonalButton(
                onClick = { showAddRecordingDialog = true },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Recording")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val tracks = playlist?.tracks ?: emptyList()
        if (tracks.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(
                    "No tracks yet — tap Add Music or Add Recording",
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

    if (showDeviceMusicPicker) {
        DeviceMusicPickerDialog(
            onDismiss = { showDeviceMusicPicker = false },
            onConfirm = { selectedTracks ->
                scope.launch {
                    selectedTracks.forEach { track ->
                        repo.addImportedTrack(
                            playlistId,
                            android.net.Uri.parse(track.uri),
                            track.title
                        )
                    }
                }
                showDeviceMusicPicker = false
            }
        )
    }

    if (showAddRecordingDialog) {
        val recordings = remember { recordingRepo.listRecordings() }
        AlertDialog(
            onDismissRequest = { showAddRecordingDialog = false },
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        scope.launch { repo.addInternalRecording(playlistId, rec) }
                                        showAddRecordingDialog = false
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddRecordingDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun DeviceMusicPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<DeviceAudioTrack>) -> Unit
) {
    val context = LocalContext.current
    val deviceRepo = remember { DeviceMusicRepository(context) }

    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.READ_MEDIA_AUDIO
    else
        Manifest.permission.READ_EXTERNAL_STORAGE

    var hasPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED)
    }
    var allTracks by remember { mutableStateOf<List<DeviceAudioTrack>>(emptyList()) }
    val selectedIds = remember { mutableStateMapOf<Long, Boolean>() }
    var query by remember { mutableStateOf("") }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) allTracks = deviceRepo.queryAllAudio()
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) allTracks = deviceRepo.queryAllAudio()
        else permissionLauncher.launch(permission)
    }

    val filtered = allTracks.filter {
        it.title.contains(query, ignoreCase = true) || it.artist.contains(query, ignoreCase = true)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add from Device") },
        text = {
            Column {
                if (!hasPermission) {
                    Text(
                        "Music access is needed to browse your device's library.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (allTracks.isEmpty()) {
                    Text("No music found on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search your music") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LazyColumn(modifier = Modifier.height(360.dp)) {
                        items(filtered, key = { it.id }) { track ->
                            val isSelected = selectedIds[track.id] == true
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedIds[track.id] = !isSelected }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isSelected, onCheckedChange = { selectedIds[track.id] = it })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val chosen = allTracks.filter { selectedIds[it.id] == true }
                onConfirm(chosen)
            }) { Text("Add Selected") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
                    if (track.isInternal) "Internal recording" else "From device",
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
