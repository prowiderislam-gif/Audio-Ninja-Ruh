package com.audioninja.app.data

import android.content.Context
import android.net.Uri
import android.media.MediaMetadataRetriever
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class PlaylistTrack(
    val id: String,
    val title: String,
    val uri: String,
    val coverPath: String?,
    val isInternal: Boolean
)

data class Playlist(
    val id: String,
    val name: String,
    val shuffle: Boolean = false,
    val loop: Boolean = false,
    val tracks: List<PlaylistTrack> = emptyList()
)

private val Context.playlistsDataStore by preferencesDataStore(name = "audio_ninja_playlists")

class PlaylistRepository(private val context: Context) {

    private val KEY = stringPreferencesKey("playlists_json")
    private val PINNED_KEY = stringPreferencesKey("pinned_playlist_ids")

    val playlists: Flow<List<Playlist>> =
        context.playlistsDataStore.data.map { prefs -> parsePlaylists(prefs[KEY] ?: "[]") }

    val pinnedPlaylistIds: Flow<List<String>> =
        context.playlistsDataStore.data.map { prefs ->
            (prefs[PINNED_KEY] ?: "").split(",").filter { it.isNotBlank() }
        }

    suspend fun setPinnedPlaylistIds(ids: List<String>) {
        context.playlistsDataStore.edit { it[PINNED_KEY] = ids.take(3).joinToString(",") }
    }

    suspend fun createPlaylist(name: String): String {
        val id = UUID.randomUUID().toString()
        updateAll { it + Playlist(id = id, name = name) }
        return id
    }

    suspend fun renamePlaylist(id: String, newName: String) {
        updateAll { list -> list.map { if (it.id == id) it.copy(name = newName) else it } }
    }

    suspend fun deletePlaylist(id: String) {
        updateAll { list -> list.filterNot { it.id == id } }
    }

    suspend fun setShuffle(id: String, shuffle: Boolean) {
        updateAll { list -> list.map { if (it.id == id) it.copy(shuffle = shuffle) else it } }
    }

    suspend fun setLoop(id: String, loop: Boolean) {
        updateAll { list -> list.map { if (it.id == id) it.copy(loop = loop) else it } }
    }

    suspend fun addInternalRecording(playlistId: String, recording: Recording) {
        val track = PlaylistTrack(
            id = UUID.randomUUID().toString(),
            title = recording.fileName,
            uri = recording.filePath,
            coverPath = null,
            isInternal = true
        )
        updateAll { list -> list.map { if (it.id == playlistId) it.copy(tracks = it.tracks + track) else it } }
    }

    /** Imports an external audio file, extracting embedded cover art if present. */
    suspend fun addImportedTrack(playlistId: String, uri: Uri, title: String) {
        val coverPath = extractCoverArt(uri)
        val track = PlaylistTrack(
            id = UUID.randomUUID().toString(),
            title = title,
            uri = uri.toString(),
            coverPath = coverPath,
            isInternal = false
        )
        updateAll { list -> list.map { if (it.id == playlistId) it.copy(tracks = it.tracks + track) else it } }
    }

    suspend fun removeTrack(playlistId: String, trackId: String) {
        updateAll { list ->
            list.map { pl ->
                if (pl.id == playlistId) pl.copy(tracks = pl.tracks.filterNot { it.id == trackId }) else pl
            }
        }
    }

    private fun extractCoverArt(uri: Uri): String? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val art = retriever.embeddedPicture
            retriever.release()
            if (art != null) {
                val coverDir = File(context.filesDir, "covers").apply { mkdirs() }
                val file = File(coverDir, "${UUID.randomUUID()}.jpg")
                file.writeBytes(art)
                file.absolutePath
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun updateAll(transform: (List<Playlist>) -> List<Playlist>) {
        context.playlistsDataStore.edit { prefs ->
            val current = parsePlaylists(prefs[KEY] ?: "[]")
            val updated = transform(current)
            prefs[KEY] = serializePlaylists(updated)
        }
    }

    private fun serializePlaylists(list: List<Playlist>): String {
        val arr = JSONArray()
        list.forEach { pl ->
            val obj = JSONObject()
            obj.put("id", pl.id)
            obj.put("name", pl.name)
            obj.put("shuffle", pl.shuffle)
            obj.put("loop", pl.loop)
            val tracksArr = JSONArray()
            pl.tracks.forEach { t ->
                val tObj = JSONObject()
                tObj.put("id", t.id)
                tObj.put("title", t.title)
                tObj.put("uri", t.uri)
                tObj.put("coverPath", t.coverPath ?: "")
                tObj.put("isInternal", t.isInternal)
                tracksArr.put(tObj)
            }
            obj.put("tracks", tracksArr)
            arr.put(obj)
        }
        return arr.toString()
    }

    private fun parsePlaylists(json: String): List<Playlist> {
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val tracksArr = obj.getJSONArray("tracks")
                val tracks = (0 until tracksArr.length()).map { j ->
                    val t = tracksArr.getJSONObject(j)
                    PlaylistTrack(
                        id = t.getString("id"),
                        title = t.getString("title"),
                        uri = t.getString("uri"),
                        coverPath = t.getString("coverPath").ifBlank { null },
                        isInternal = t.getBoolean("isInternal")
                    )
                }
                Playlist(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    shuffle = obj.optBoolean("shuffle", false),
                    loop = obj.optBoolean("loop", false),
                    tracks = tracks
                )
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
