package com.audioninja.app.data

data class Recording(
    val id: String,
    val fileName: String,
    val filePath: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateCreated: Long,
    val format: String,
    val bitrate: Int,
    val sampleRate: Int,
    val isFavorite: Boolean = false,
    val folder: String = "All"
)

enum class SortOption { DATE, NAME, SIZE, DURATION }

enum class OutputFormat(val extension: String, val mimeType: String) {
    M4A("m4a", "audio/mp4"),
    WAV("wav", "audio/wav"),
    AAC("aac", "audio/aac")
}
