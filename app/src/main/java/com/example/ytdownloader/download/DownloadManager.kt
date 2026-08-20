package com.example.ytdownloader.download

import com.example.ytdownloader.model.DownloadType
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.util.UUID

/**
 * Owns every in-flight and recently finished download.
 * Lives outside the Activity lifecycle so downloads survive tab switches
 * and rotation, and so multiple downloads can run at the same time.
 */
object DownloadManager {

    private val managerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    fun startDownload(
        downloadDirectory: File,
        url: String,
        downloadType: DownloadType,
        quality: Int?
    ): String {
        val id = UUID.randomUUID().toString()

        _tasks.update { current ->
            current + DownloadTask(id = id, url = url, downloadType = downloadType, quality = quality)
        }

        val job = managerScope.launch {
            try {
                val file = YoutubeDownloader.downloadMedia(
                    downloadDirectory = downloadDirectory,
                    url = url,
                    downloadType = downloadType,
                    selectedQuality = quality,
                    processId = id,
                    onProgress = { progress -> updateTask(id) { it.copy(progress = progress) } },
                    onOutput = { output -> maybeUpdateTitle(id, output) }
                )

                updateTask(id) {
                    it.copy(status = DownloadStatus.COMPLETE, progress = 100f, resultFile = file)
                }
            } catch (e: CancellationException) {
                // Status is already set to CANCELLED by cancelDownload(); just let the job end.
                throw e
            } catch (e: Exception) {
                updateTask(id) { current ->
                    if (current.status == DownloadStatus.CANCELLED) current
                    else current.copy(status = DownloadStatus.ERROR, errorMessage = e.message ?: "Unknown error")
                }
            } finally {
                jobs.remove(id)
            }
        }

        jobs[id] = job
        return id
    }

    fun cancelDownload(id: String) {
        updateTask(id) { it.copy(status = DownloadStatus.CANCELLED) }
        try {
            YoutubeDL.getInstance().destroyProcessById(id)
        } catch (e: Exception) {
            // Process may have already finished; safe to ignore.
        }
        jobs[id]?.cancel()
    }

    fun dismiss(id: String) {
        _tasks.update { list -> list.filterNot { it.id == id } }
    }

    private fun maybeUpdateTitle(id: String, output: String) {
        val isTitleLine = output.isNotBlank() &&
                !output.startsWith("[") &&
                !output.contains("Downloading") &&
                !output.contains("Destination:") &&
                !output.contains("Merging") &&
                !output.contains("Extracting") &&
                !output.contains("Download complete")

        if (isTitleLine) {
            updateTask(id) { it.copy(title = output.trim()) }
        }
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }
}