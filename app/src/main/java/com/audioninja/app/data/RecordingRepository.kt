package com.audioninja.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.UUID

/**
 * Scans the app's private recordings directory and builds Recording entries.
 * Favorites/folder assignment are kept in a lightweight sidecar map for now;
 * swap for Room if/when the library needs richer querying.
 */
class RecordingRepository(private val context: Context) {

    fun recordingsDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listRecordings(): List<Recording> {
        val dir = recordingsDir()
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in listOf("m4a", "wav", "aac", "mp3") }
            ?: emptyArray()

        return files.map { file ->
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            var sampleRate = 48000
            try {
                retriever.setDataSource(file.absolutePath)
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            } catch (_: Exception) {
                // Corrupt or in-progress file; fall back to defaults.
            } finally {
                retriever.release()
            }

            Recording(
                id = UUID.nameUUIDFromBytes(file.absolutePath.toByteArray()).toString(),
                fileName = file.nameWithoutExtension,
                filePath = file.absolutePath,
                durationMs = durationMs,
                sizeBytes = file.length(),
                dateCreated = file.lastModified(),
                format = file.extension.uppercase(),
                bitrate = 320000,
                sampleRate = sampleRate
            )
        }.sortedByDescending { it.dateCreated }
    }

    fun rename(recording: Recording, newName: String): Boolean {
        val oldFile = File(recording.filePath)
        val newFile = File(oldFile.parentFile, "$newName.${oldFile.extension}")
        return oldFile.renameTo(newFile)
    }

    fun delete(recording: Recording): Boolean = File(recording.filePath).delete()

    fun duplicate(recording: Recording): Boolean {
        val src = File(recording.filePath)
        val dest = File(src.parentFile, "${src.nameWithoutExtension}_copy.${src.extension}")
        return try {
            src.copyTo(dest, overwrite = false)
            true
        } catch (_: Exception) {
            false
        }
    }
}
