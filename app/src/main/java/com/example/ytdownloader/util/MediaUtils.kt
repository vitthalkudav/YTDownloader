package com.example.ytdownloader.util

import java.io.File

object MediaUtils {

    private val supportedExtensions = setOf(
        "mp4", "mkv", "webm", "mp3", "m4a", "opus", "aac", "wav"
    )

    fun getMediaFiles(directory: File): List<File> {
        if (!directory.exists()) return emptyList()

        return directory.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in supportedExtensions }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun formatFileSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024L * 1024L * 1024L -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}