package com.example.ytdownloader.download

import com.example.ytdownloader.model.DownloadType
import java.io.File

enum class DownloadStatus {
    RUNNING,
    COMPLETE,
    ERROR,
    CANCELLED
}

data class DownloadTask(
    val id: String,
    val url: String,
    val downloadType: DownloadType,
    val quality: Int?,
    val title: String = "",
    val progress: Float = 0f,
    val status: DownloadStatus = DownloadStatus.RUNNING,
    val errorMessage: String? = null,
    val resultFile: File? = null
)