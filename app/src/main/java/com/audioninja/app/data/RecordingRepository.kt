package com.audioninja.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import java.io.File
import java.util.UUID

/**
 * Scans the recordings directory and builds Recording entries. Storage location is
 * chosen by the "Save to External Storage" setting:
 * - OFF (private): app-internal storage, not visible outside the app, cleared if the app is uninstalled
 * - ON (external): app-specific external storage, visible via a file manager under
 *   Android/data/com.audioninja.app/files/Recordings — no extra permission needed
 */
class RecordingRepository(private val context: Context) {

    fun recordingsDir(saveToExternal: Boolean = false): File {
        val baseDir = if (saveToExternal) {
            context.getExternalFilesDir(null) ?: context.filesDir
        } else {
            context.filesDir
        }
        val dir = File(baseDir, "Recordings")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun storagePathDisplay(saveToExternal: Boolean): String {
        return if (saveToExternal) {
            "Android/data/${context.packageName}/files/Recordings (visible in Files app)"
        } else {
            "App private storage (only visible inside Audio Ninja)"
        }
    }

    fun listRecordings(saveToExternal: Boolean = false): List<Recording> {
        val dir = recordingsDir(saveToExternal)
        val files = dir.listFiles { f -> f.isFile && f.extension.lowercase() in listOf("m4a", "wav", "aac", "mp3") }
            ?: emptyArray()

        return files.map { file ->
            val retriever = MediaMetadataRetriever()
            var durationMs = 0L
            val sampleRate = 48000
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
