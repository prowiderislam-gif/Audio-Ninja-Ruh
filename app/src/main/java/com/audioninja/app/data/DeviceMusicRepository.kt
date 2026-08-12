package com.audioninja.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

data class DeviceAudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: String
)

/** Scans the device's shared music library via MediaStore, like any standard music app. */
class DeviceMusicRepository(private val context: Context) {

    fun queryAllAudio(): List<DeviceAudioTrack> {
        val tracks = mutableListOf<DeviceAudioTrack>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        try {
            context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol) ?: "Unknown"
                    val artist = cursor.getString(artistCol) ?: "Unknown artist"
                    val duration = cursor.getLong(durationCol)
                    val uri = ContentUris.withAppendedId(collection, id).toString()
                    tracks.add(DeviceAudioTrack(id, title, artist, duration, uri))
                }
            }
        } catch (_: Exception) {
            // Permission not yet granted or query failed — caller shows empty state.
        }
        return tracks
    }
}
