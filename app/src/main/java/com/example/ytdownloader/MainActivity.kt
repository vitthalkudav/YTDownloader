
package com.example.ytdownloader

import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.util.Log

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh

import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

import androidx.compose.ui.platform.LocalConfiguration

import androidx.lifecycle.lifecycleScope

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

import com.example.ytdownloader.ui.theme.YTDownloaderTheme

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import java.io.File

import androidx.compose.material3.ExperimentalMaterial3Api


class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "YTDownloader"
    }

    /*
     * Download directory
     */
    private lateinit var downloadDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // =========================================================
        // DOWNLOAD DIRECTORY
        // =========================================================

        downloadDirectory = File(
            Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            ),
            "YTDownloader"
        )

        if (!downloadDirectory.exists()) {
            downloadDirectory.mkdirs()
        }

        Log.d(
            TAG,
            "Download directory: ${downloadDirectory.absolutePath}"
        )

        // =========================================================
        // INITIALIZE YT-DLP
        // =========================================================

        try {

            YoutubeDL.getInstance().init(this)

            Log.d(
                TAG,
                "yt-dlp environment initialized successfully"
            )

            // -----------------------------------------------------
            // Check FFmpeg
            // -----------------------------------------------------

            val nativeDir = applicationInfo.nativeLibraryDir

            val ffmpegFile = File(
                nativeDir,
                "libffmpeg.so"
            )

            Log.d(
                TAG,
                "Native library directory: $nativeDir"
            )

            Log.d(
                TAG,
                "FFmpeg exists: ${ffmpegFile.exists()}"
            )

            Log.d(
                TAG,
                "FFmpeg executable: ${ffmpegFile.canExecute()}"
            )

            Log.d(
                TAG,
                "FFmpeg path: ${ffmpegFile.absolutePath}"
            )

            // -----------------------------------------------------
            // Test FFmpeg
            // -----------------------------------------------------

            try {

                val process = ProcessBuilder(
                    ffmpegFile.absolutePath,
                    "-version"
                )
                    .redirectErrorStream(true)
                    .start()

                val output = process.inputStream
                    .bufferedReader()
                    .readText()

                val exitCode = process.waitFor()

                Log.d(
                    TAG,
                    "FFmpeg test exit code: $exitCode"
                )

                Log.d(
                    TAG,
                    "FFmpeg test output: $output"
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "FFmpeg test failed",
                    e
                )
            }

            // -----------------------------------------------------
            // Update yt-dlp
            // -----------------------------------------------------

            Thread {

                try {

                    val result =
                        YoutubeDL.getInstance().updateYoutubeDL(
                            this,
                            YoutubeDL.UpdateChannel.STABLE
                        )

                    Log.d(
                        TAG,
                        "yt-dlp update result: $result"
                    )

                } catch (e: Exception) {

                    Log.e(
                        TAG,
                        "yt-dlp update failed",
                        e
                    )
                }

            }.start()

        } catch (e: YoutubeDLException) {

            Log.e(
                TAG,
                "yt-dlp initialization failed",
                e
            )
        }

        // =========================================================
        // COMPOSE UI
        // =========================================================

        setContent {

            YTDownloaderTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    DownloaderApp(
                        downloadDirectory = downloadDirectory,

                        onDownload = {
                                url,
                                onProgress,
                                onOutput,
                                onComplete ->

                            downloadVideo(
                                url = url,
                                onProgress = onProgress,
                                onOutput = onOutput,
                                onComplete = onComplete
                            )
                        }
                    )
                }
            }
        }
    }

    // =============================================================
    // DOWNLOAD VIDEO
    // =============================================================

    private fun downloadVideo(
        url: String,
        onProgress: (Float) -> Unit,
        onOutput: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {

        lifecycleScope.launch {

            try {

                val result = withContext(Dispatchers.IO) {

                    // -------------------------------------------------
                    // Make sure directory exists
                    // -------------------------------------------------

                    if (!downloadDirectory.exists()) {
                        downloadDirectory.mkdirs()
                    }

                    Log.d(
                        TAG,
                        "Download directory: " +
                                downloadDirectory.absolutePath
                    )

                    // -------------------------------------------------
                    // yt-dlp request
                    // -------------------------------------------------

                    val request =
                        YoutubeDLRequest(url)

                    // -------------------------------------------------
                    // Output filename
                    // -------------------------------------------------

                    request.addOption(
                        "-o",
                        "${downloadDirectory.absolutePath}/%(title)s.%(ext)s"
                    )

                    // -------------------------------------------------
                    // BEST VIDEO + BEST AUDIO
                    // -------------------------------------------------

                    request.addOption(
                        "-f",
                        "bv*+ba/b"
                    )

                    // -------------------------------------------------
                    // Merge into MP4
                    // -------------------------------------------------

                    request.addOption(
                        "--merge-output-format",
                        "mp4"
                    )

                    // -------------------------------------------------
                    // Do not keep separate video/audio files
                    // -------------------------------------------------

                    request.addOption(
                        "--no-keep-video"
                    )

                    // -------------------------------------------------
                    // Execute yt-dlp
                    // -------------------------------------------------

                    YoutubeDL
                        .getInstance()
                        .execute(
                            request
                        ) {
                                currentProgress,
                                etaInSeconds,
                                output ->

                            val cleanOutput =
                                output
                                    .toString()
                                    .trim()

                            Log.d(
                                TAG,
                                "yt-dlp: $cleanOutput"
                            )

                            // -----------------------------
                            // Progress
                            // -----------------------------

                            onProgress(
                                currentProgress
                            )

                            // -----------------------------
                            // Send output to UI
                            // -----------------------------

                            onOutput(
                                cleanOutput
                            )
                        }

                    // -------------------------------------------------
                    // Find resulting MP4
                    // -------------------------------------------------

                    val mp4File =
                        downloadDirectory
                            .listFiles()
                            ?.filter {
                                it.isFile &&
                                        it.extension.equals(
                                            "mp4",
                                            ignoreCase = true
                                        )
                            }
                            ?.maxByOrNull {
                                it.lastModified()
                            }

                    Pair(
                        downloadDirectory.absolutePath,
                        mp4File
                    )
                }

                // =====================================================
                // DOWNLOAD COMPLETE
                // =====================================================

                val downloadPath =
                    result.first

                val mp4File =
                    result.second

                onProgress(100f)

                if (mp4File != null) {

                    onOutput(
                        "Download complete!"
                    )

                    Log.d(
                        TAG,
                        "Downloaded file: " +
                                mp4File.absolutePath
                    )

                    onComplete(true)

                } else {

                    onOutput(
                        "Download finished, but MP4 file was not found"
                    )

                    Log.e(
                        TAG,
                        "No MP4 file found in: $downloadPath"
                    )

                    onComplete(false)
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Download failed",
                    e
                )

                onOutput(
                    "ERROR: ${e.message ?: "Unknown error"}"
                )

                onComplete(false)
            }
        }
    }
}


// =================================================================
// MAIN APP
// =================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderApp(
    downloadDirectory: File,
    onDownload: (
        String,
        (Float) -> Unit,
        (String) -> Unit,
        (Boolean) -> Unit
    ) -> Unit
) {

    // -------------------------------------------------------------
    // Selected tab
    // -------------------------------------------------------------

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    // -------------------------------------------------------------
    // Downloaded videos
    // -------------------------------------------------------------

    var downloadedVideos by remember {

        mutableStateOf(
            getVideoFiles(
                downloadDirectory
            )
        )
    }

    // -------------------------------------------------------------
    // Selected video
    // -------------------------------------------------------------

    var selectedVideo by remember {
        mutableStateOf<File?>(null)
    }

    // -------------------------------------------------------------
    // Download state
    // -------------------------------------------------------------

    var url by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf("Ready")
    }

    var progress by remember {
        mutableFloatStateOf(0f)
    }

    var videoTitle by remember {
        mutableStateOf("")
    }

    var downloading by remember {
        mutableStateOf(false)
    }

    // -------------------------------------------------------------
    // Video player
    // -------------------------------------------------------------

    if (selectedVideo != null) {

        VideoPlayerScreen(
            videoFile = selectedVideo!!,
            onBack = {
                selectedVideo = null
            }
        )

        return
    }

    // =============================================================
    // MAIN UI
    // =============================================================

    Scaffold(

        topBar = {

            TopAppBar(
                title = {
                    Text("YT Downloader")
                }
            )
        },

        bottomBar = {

            NavigationBar {

                // -------------------------------------------------
                // DOWNLOAD TAB
                // -------------------------------------------------

                NavigationBarItem(

                    selected = selectedTab == 0,

                    onClick = {
                        selectedTab = 0
                    },

                    icon = {

                        Icon(
                            imageVector =
                                Icons.Default.Download,

                            contentDescription =
                                "Download"
                        )
                    },

                    label = {
                        Text("Download")
                    }
                )

                // -------------------------------------------------
                // DOWNLOADS TAB
                // -------------------------------------------------

                NavigationBarItem(

                    selected = selectedTab == 1,

                    onClick = {

                        selectedTab = 1

                        downloadedVideos =
                            getVideoFiles(
                                downloadDirectory
                            )
                    },

                    icon = {

                        Icon(
                            imageVector =
                                Icons.Default.PlayArrow,

                            contentDescription =
                                "Downloads"
                        )
                    },

                    label = {
                        Text("Downloads")
                    }
                )
            }
        }

    ) { paddingValues ->

        when (selectedTab) {

            // =====================================================
            // DOWNLOAD TAB
            // =====================================================

            0 -> {

                DownloadScreen(

                    modifier =
                        Modifier.padding(
                            paddingValues
                        ),

                    url = url,

                    onUrlChange = {
                        url = it
                    },

                    status = status,

                    progress = progress,

                    videoTitle = videoTitle,

                    downloading = downloading,

                    onDownload = {

                        // -----------------------------------------
                        // Validate URL
                        // -----------------------------------------

                        if (url.isBlank()) {

                            status =
                                "Please enter a YouTube URL"

                            return@DownloadScreen
                        }

                        // -----------------------------------------
                        // Prevent duplicate download
                        // -----------------------------------------

                        if (downloading) {
                            return@DownloadScreen
                        }

                        // -----------------------------------------
                        // Reset
                        // -----------------------------------------

                        status =
                            "Starting download..."

                        progress = 0f

                        videoTitle = ""

                        downloading = true

                        // -----------------------------------------
                        // Start download
                        // -----------------------------------------

                        onDownload(

                            url,

                            // -------------------------------------
                            // Progress
                            // -------------------------------------

                            { currentProgress ->

                                progress =
                                    currentProgress
                            },

                            // -------------------------------------
                            // Output
                            // -------------------------------------

                            { output ->

                                if (output.isNotBlank()) {

                                    Log.d(
                                        MainActivity.TAG,
                                        "UI output: $output"
                                    )
                                }

                                // -----------------------------
                                // Error
                                // -----------------------------

                                if (
                                    output.startsWith(
                                        "ERROR:"
                                    )
                                ) {

                                    status =
                                        output
                                }

                                // -----------------------------
                                // Extract title
                                // -----------------------------

                                if (
                                    output.isNotBlank() &&
                                    !output.startsWith("[") &&
                                    !output.contains("Downloading") &&
                                    !output.contains("Destination:") &&
                                    !output.contains("Merging") &&
                                    !output.startsWith("ERROR:")
                                ) {

                                    videoTitle =
                                        output.trim()
                                }
                            },

                            // -------------------------------------
                            // Complete
                            // -------------------------------------

                            { success ->

                                downloading = false

                                if (success) {

                                    progress = 100f

                                    status =
                                        "Download complete!"

                                    downloadedVideos =
                                        getVideoFiles(
                                            downloadDirectory
                                        )

                                } else {

                                    if (
                                        !status.startsWith(
                                            "ERROR:"
                                        )
                                    ) {

                                        status =
                                            "Download failed"
                                    }
                                }
                            }
                        )

                        status =
                            "Downloading..."
                    }
                )
            }

            // =====================================================
            // DOWNLOADS TAB
            // =====================================================

            1 -> {

                DownloadsScreen(

                    modifier =
                        Modifier.padding(
                            paddingValues
                        ),

                    videos =
                        downloadedVideos,

                    onRefresh = {

                        downloadedVideos =
                            getVideoFiles(
                                downloadDirectory
                            )
                    },

                    onVideoClick = { file ->

                        selectedVideo = file
                    }
                )
            }
        }
    }
}


// =================================================================
// DOWNLOAD SCREEN
// =================================================================

@Composable
fun DownloadScreen(
    modifier: Modifier,
    url: String,
    onUrlChange: (String) -> Unit,
    status: String,
    progress: Float,
    videoTitle: String,
    downloading: Boolean,
    onDownload: () -> Unit
) {

    Column(

        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.Center
    ) {

        // ---------------------------------------------------------
        // Title
        // ---------------------------------------------------------

        Text(

            text = "YT Downloader",

            style =
                MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        // ---------------------------------------------------------
        // URL
        // ---------------------------------------------------------

        OutlinedTextField(

            value = url,

            onValueChange = onUrlChange,

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text("YouTube URL")
            },

            placeholder = {
                Text("Paste YouTube URL here")
            },

            singleLine = true,

            enabled = !downloading
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        // ---------------------------------------------------------
        // Download button
        // ---------------------------------------------------------

        Button(

            enabled = !downloading,

            onClick = onDownload,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(

                if (downloading) {
                    "Downloading..."
                } else {
                    "Download Video"
                }
            )
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        // ---------------------------------------------------------
        // Video title
        // ---------------------------------------------------------

        if (videoTitle.isNotBlank()) {

            Text(

                text =
                    "Video: $videoTitle",

                style =
                    MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )
        }

        // ---------------------------------------------------------
        // Status
        // ---------------------------------------------------------

        Text(

            text =
                "Status: $status",

            style =
                MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        // ---------------------------------------------------------
        // Progress
        // ---------------------------------------------------------

        Text(

            text =
                "Progress: ${progress.toInt()}%"
        )
    }
}


// =================================================================
// DOWNLOADS SCREEN
// =================================================================

@Composable
fun DownloadsScreen(
    modifier: Modifier,
    videos: List<File>,
    onRefresh: () -> Unit,
    onVideoClick: (File) -> Unit
) {

    Column(

        modifier =
            modifier
                .fillMaxSize()
                .padding(16.dp)
    ) {

        // ---------------------------------------------------------
        // Header
        // ---------------------------------------------------------

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(

                text =
                    "Downloaded Videos",

                style =
                    MaterialTheme.typography.headlineSmall,

                modifier =
                    Modifier.weight(1f)
            )

            IconButton(
                onClick = onRefresh
            ) {

                Icon(

                    imageVector =
                        Icons.Default.Refresh,

                    contentDescription =
                        "Refresh"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        // ---------------------------------------------------------
        // Empty state
        // ---------------------------------------------------------

        if (videos.isEmpty()) {

            Column(

                modifier =
                    Modifier.fillMaxSize(),

                verticalArrangement =
                    Arrangement.Center,

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text =
                        "No downloaded videos"
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "Download a video and it will appear here."
                )
            }

        } else {

            // -----------------------------------------------------
            // Video list
            // -----------------------------------------------------

            LazyColumn {

                items(

                    items = videos,

                    key = {
                        it.absolutePath
                    }

                ) { file ->

                    VideoListItem(

                        file = file,

                        onClick = {
                            onVideoClick(file)
                        }
                    )
                }
            }
        }
    }
}


// =================================================================
// VIDEO LIST ITEM
// =================================================================

@Composable
fun VideoListItem(
    file: File,
    onClick: () -> Unit
) {

    Card(

        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    vertical = 6.dp
                )
                .clickable {
                    onClick()
                }
    ) {

        Row(

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            // -----------------------------------------------------
            // Play icon
            // -----------------------------------------------------

            Icon(

                imageVector =
                    Icons.Default.PlayArrow,

                contentDescription =
                    "Play",

                modifier =
                    Modifier.size(40.dp)
            )

            Spacer(
                modifier =
                    Modifier.size(16.dp)
            )

            // -----------------------------------------------------
            // File information
            // -----------------------------------------------------

            Column(

                modifier =
                    Modifier.weight(1f)
            ) {

                Text(

                    text =
                        file.name.substringBeforeLast(
                            "."
                        ),

                    style =
                        MaterialTheme.typography.titleMedium,

                    maxLines = 2,

                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(

                    text =
                        formatFileSize(
                            file.length()
                        ),

                    style =
                        MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}


// =================================================================
// VIDEO PLAYER
// =================================================================

@Composable
fun VideoPlayerScreen(
    videoFile: File,
    onBack: () -> Unit
) {

    val context =
        androidx.compose.ui.platform
            .LocalContext.current

    // -------------------------------------------------------------
    // Detect orientation
    // -------------------------------------------------------------

    val configuration =
        LocalConfiguration.current

    val isLandscape =
        configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE

    // -------------------------------------------------------------
    // ExoPlayer
    // -------------------------------------------------------------

    val exoPlayer = remember {

        ExoPlayer
            .Builder(context)
            .build()
            .apply {

                val mediaItem =
                    MediaItem.fromUri(
                        android.net.Uri.fromFile(
                            videoFile
                        )
                    )

                setMediaItem(
                    mediaItem
                )

                prepare()

                playWhenReady = true
            }
    }

    // -------------------------------------------------------------
    // Release player
    // -------------------------------------------------------------

    DisposableEffect(
        exoPlayer
    ) {

        onDispose {

            exoPlayer.release()
        }
    }

    // =============================================================
    // LANDSCAPE MODE
    // =============================================================

    if (isLandscape) {

        AndroidView(

            factory = { ctx ->

                PlayerView(ctx).apply {

                    player =
                        exoPlayer

                    useController = true
                }
            },

            modifier =
                Modifier.fillMaxSize()
        )

        return
    }

    // =============================================================
    // PORTRAIT MODE
    // =============================================================

    Column(

        modifier =
            Modifier.fillMaxSize()
    ) {

        // ---------------------------------------------------------
        // Back button
        // ---------------------------------------------------------

        Button(

            onClick = onBack,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        ) {

            Text(
                "Back to Downloads"
            )
        }

        // ---------------------------------------------------------
        // Video title
        // ---------------------------------------------------------

        Text(

            text =
                videoFile.name.substringBeforeLast(
                    "."
                ),

            style =
                MaterialTheme.typography.titleMedium,

            modifier =
                Modifier.padding(
                    horizontal = 16.dp
                ),

            maxLines = 2,

            overflow =
                TextOverflow.Ellipsis
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        // ---------------------------------------------------------
        // Media3 PlayerView
        // ---------------------------------------------------------

        AndroidView(

            factory = { ctx ->

                PlayerView(ctx).apply {

                    player =
                        exoPlayer

                    useController = true
                }
            },

            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
        )
    }
}


// =================================================================
// GET VIDEO FILES
// =================================================================

fun getVideoFiles(
    directory: File
): List<File> {

    if (!directory.exists()) {
        return emptyList()
    }

    return directory
        .listFiles()
        ?.filter {

            it.isFile &&
                    it.extension.equals(
                        "mp4",
                        ignoreCase = true
                    )
        }
        ?.sortedByDescending {

            it.lastModified()
        }
        ?: emptyList()
}


// =================================================================
// FORMAT FILE SIZE
// =================================================================

fun formatFileSize(
    bytes: Long
): String {

    if (bytes < 1024) {
        return "$bytes B"
    }

    if (bytes < 1024 * 1024) {

        return String.format(
            "%.1f KB",
            bytes / 1024.0
        )
    }

    if (bytes < 1024L * 1024L * 1024L) {

        return String.format(
            "%.1f MB",
            bytes /
                    (1024.0 * 1024.0)
        )
    }

    return String.format(
        "%.2f GB",
        bytes /
                (1024.0 * 1024.0 * 1024.0)
    )
}
