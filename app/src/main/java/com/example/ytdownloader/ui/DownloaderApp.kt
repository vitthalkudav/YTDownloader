package com.example.ytdownloader.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.ytdownloader.model.DownloadType
import com.example.ytdownloader.util.MediaUtils
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderApp(
    downloadDirectory: File,
    onDownload: (String, DownloadType, Int?, (Float) -> Unit, (String) -> Unit, (Boolean) -> Unit) -> Unit,
    onGetAvailableQualities: (String, (List<Int>) -> Unit, (String) -> Unit) -> Unit
) {
    val context = LocalContext.current

    var selectedTab by remember { mutableIntStateOf(0) }
    var downloadedVideos by remember { mutableStateOf(MediaUtils.getMediaFiles(downloadDirectory)) }
    var selectedVideo by remember { mutableStateOf<File?>(null) }

    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("Ready") }
    var progress by remember { mutableFloatStateOf(0f) }
    var videoTitle by remember { mutableStateOf("") }
    var downloading by remember { mutableStateOf(false) }

    var availableQualities by remember { mutableStateOf<List<Int>>(emptyList()) }
    var selectedQuality by remember { mutableStateOf<Int?>(null) }
    var fetchingQualities by remember { mutableStateOf(false) }

    var downloadType by remember { mutableStateOf(DownloadType.VIDEO) }

    if (selectedVideo != null) {
        VideoPlayerScreen(videoFile = selectedVideo!!, onBack = { selectedVideo = null })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("YT Downloader") }) },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Download, contentDescription = "Download") },
                    label = { Text("Download") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        downloadedVideos = MediaUtils.getMediaFiles(downloadDirectory)
                    },
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Downloads") },
                    label = { Text("Downloads") }
                )
            }
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> DownloadScreen(
                modifier = Modifier.padding(paddingValues),
                url = url,
                onUrlChange = {
                    url = it
                    availableQualities = emptyList()
                    selectedQuality = null
                    status = if (it.isBlank()) "Ready" else "Enter URL and get available qualities"
                },
                status = status,
                progress = progress,
                videoTitle = videoTitle,
                downloading = downloading,
                availableQualities = availableQualities,
                selectedQuality = selectedQuality,
                fetchingQualities = fetchingQualities,
                downloadType = downloadType,
                onDownloadTypeSelected = {
                    downloadType = it
                    if (it == DownloadType.AUDIO) {
                        selectedQuality = null
                        availableQualities = emptyList()
                        status = "Audio-only download selected"
                    } else {
                        status = "Video download selected"
                    }
                },
                onQualitySelected = {
                    selectedQuality = it
                    status = if (it == null) "Best available quality selected" else "${it}p selected"
                },
                onFetchQualities = {
                    if (url.isBlank()) { status = "Please enter a YouTube URL"; return@DownloadScreen }
                    if (fetchingQualities) return@DownloadScreen

                    fetchingQualities = true
                    availableQualities = emptyList()
                    selectedQuality = null
                    status = "Fetching available qualities..."

                    onGetAvailableQualities(
                        url,
                        { qualities ->
                            fetchingQualities = false
                            availableQualities = qualities
                            status = if (qualities.isEmpty()) "No video qualities found" else "Available qualities loaded"
                        },
                        { error ->
                            fetchingQualities = false
                            status = "ERROR: $error"
                        }
                    )
                },
                onDownload = {
                    if (url.isBlank()) { status = "Please enter a YouTube URL"; return@DownloadScreen }
                    if (downloading) return@DownloadScreen

                    status = "Starting download..."
                    progress = 0f
                    videoTitle = ""
                    downloading = true

                    onDownload(
                        url, downloadType, selectedQuality,
                        { currentProgress -> progress = currentProgress },
                        { output ->
                            if (output.startsWith("ERROR:")) status = output

                            if (output.isNotBlank() &&
                                !output.startsWith("[") &&
                                !output.startsWith("ERROR:") &&
                                !output.contains("Downloading") &&
                                !output.contains("Destination:") &&
                                !output.contains("Merging") &&
                                !output.contains("Extracting") &&
                                !output.contains("Download complete")
                            ) {
                                videoTitle = output.trim()
                            }
                        },
                        { success ->
                            downloading = false
                            if (success) {
                                progress = 100f
                                status = "Download complete!"
                                downloadedVideos = MediaUtils.getMediaFiles(downloadDirectory)
                            } else if (!status.startsWith("ERROR:")) {
                                status = "Download failed"
                            }
                        }
                    )

                    status = if (downloadType == DownloadType.AUDIO) "Downloading audio..." else "Downloading video..."
                },
                onPaste = {
                    try {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = clipboard.primaryClip

                        if (!clipboard.hasPrimaryClip() || clip == null || clip.itemCount == 0) {
                            status = "Clipboard is empty"
                            return@DownloadScreen
                        }

                        val pastedText = clip.getItemAt(0).coerceToText(context).toString().trim()
                        if (pastedText.isBlank()) {
                            status = "Clipboard does not contain text"
                            return@DownloadScreen
                        }

                        url = pastedText
                        availableQualities = emptyList()
                        selectedQuality = null
                        status = "URL pasted from clipboard"
                    } catch (e: Exception) {
                        status = "Unable to read clipboard"
                    }
                }
            )

            1 -> DownloadsScreen(
                modifier = Modifier.padding(paddingValues),
                videos = downloadedVideos,
                onRefresh = { downloadedVideos = MediaUtils.getMediaFiles(downloadDirectory) },
                onVideoClick = { file -> selectedVideo = file }
            )
        }
    }
}