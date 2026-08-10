package com.audioninja.app.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.os.Environment
import java.io.File
import java.util.UUID

/**
 * Recordings are saved to the public Downloads folder under "Audio Ninja" so they're
 * visible in any file manager app — Download/Audio Ninja/. This requires the
 * "All files access" permission on Android 11+ (requested via Settings, not a normal
 * runtime dialog). Falls back to app-private storage if that permission isn't granted.
 */
class RecordingRepository(private val context: Context) {

    fun recordingsDir(saveToExternal: Boolean = true): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, "Audio Ninja")

        val dir = if (canWriteToPublicStorage() ) {
            targetDir
        } else {
            File(context.getExternalFilesDir(null) ?: context.filesDir, "Recordings")
        }

        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun canWriteToPublicStorage(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun storagePathDisplay(saveToExternal: Boolean): String {
        return if (canWriteToPublicStorage()) {
            "Download/Audio Ninja (visible in your Files app)"
        } else {
            "App private storage — grant \"All files access\" in Settings to save to Download/Audio Ninja instead"
        }
    }

    fun listRecordings(saveToExternal: Boolean = true): List<Recording> {
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
