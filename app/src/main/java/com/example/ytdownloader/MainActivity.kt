
package com.example.ytdownloader

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
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
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import org.json.JSONObject
import java.io.File

// =================================================================
// MAIN ACTIVITY
// =================================================================

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "YTDownloader"
    }

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

            YoutubeDL
                .getInstance()
                .init(this)

            Log.d(
                TAG,
                "yt-dlp environment initialized successfully"
            )

            // =====================================================
            // FFmpeg INFORMATION
            // =====================================================
            //
            // IMPORTANT:
            // libffmpeg.so is a shared library and must NOT be
            // executed directly using ProcessBuilder.
            //
            // The previous code attempted:
            //
            // ProcessBuilder(libffmpeg.so, "-version")
            //
            // which resulted in:
            //
            // CANNOT LINK EXECUTABLE:
            // library "libavdevice.so.61" not found
            //
            // Therefore we only log the native library directory.
            // =====================================================

            Log.d(
                TAG,
                "Native library directory: " +
                        applicationInfo.nativeLibraryDir
            )

            // =====================================================
            // UPDATE YT-DLP
            // =====================================================

            Thread {

                try {

                    val result =
                        YoutubeDL
                            .getInstance()
                            .updateYoutubeDL(
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

                        downloadDirectory =
                            downloadDirectory,

                        onDownload = {
                                url,
                                downloadType,
                                selectedQuality,
                                onProgress,
                                onOutput,
                                onComplete ->

                            downloadMedia(
                                url =
                                    url,

                                downloadType =
                                    downloadType,

                                selectedQuality =
                                    selectedQuality,

                                onProgress =
                                    onProgress,

                                onOutput =
                                    onOutput,

                                onComplete =
                                    onComplete
                            )
                        },

                        onGetAvailableQualities = {
                                url,
                                onQualities,
                                onError ->

                            getAvailableQualities(
                                url =
                                    url,

                                onQualities =
                                    onQualities,

                                onError =
                                    onError
                            )
                        }
                    )
                }
            }
        }
    }

    // =============================================================
    // DOWNLOAD MEDIA
    // =============================================================

    private fun downloadMedia(
        url: String,
        downloadType: DownloadType,
        selectedQuality: Int?,
        onProgress: (Float) -> Unit,
        onOutput: (String) -> Unit,
        onComplete: (Boolean) -> Unit
    ) {

        lifecycleScope.launch {

            try {

                val result =
                    withContext(Dispatchers.IO) {

                        // =================================================
                        // MAKE SURE DIRECTORY EXISTS
                        // =================================================

                        if (!downloadDirectory.exists()) {

                            if (!downloadDirectory.mkdirs()) {

                                throw Exception(
                                    "Unable to create download directory"
                                )
                            }
                        }

                        Log.d(
                            TAG,
                            "Download directory: " +
                                    downloadDirectory.absolutePath
                        )

                        // =================================================
                        // RECORD EXISTING FILES
                        // =================================================

                        val existingFiles: Set<String> =
                            downloadDirectory
                                .listFiles()
                                ?.map {
                                    it.absolutePath
                                }
                                ?.toSet()
                                ?: emptySet()

                        // =================================================
                        // CREATE REQUEST
                        // =================================================

                        val request =
                            YoutubeDLRequest(url)

                        // =================================================
                        // OUTPUT TEMPLATE
                        // =================================================

                        request.addOption(
                            "-o",
                            "${downloadDirectory.absolutePath}/%(title)s.%(ext)s"
                        )

                        // =================================================
                        // NETWORK OPTIONS
                        // =================================================

                        request.addOption(
                            "--force-ipv4"
                        )

                        request.addOption(
                            "--retries",
                            "10"
                        )

                        request.addOption(
                            "--fragment-retries",
                            "10"
                        )

                        request.addOption(
                            "--socket-timeout",
                            "30"
                        )

                        request.addOption(
                            "--retry-sleep",
                            "1"
                        )

                        // =================================================
                        // DO NOT FORCE TV CLIENT
                        // =================================================
                        //
                        // We previously used:
                        //
                        // youtube:player_client=tv
                        //
                        // That caused:
                        //
                        // Requested format is not available
                        //
                        // for the video being tested.
                        //
                        // yt-dlp is therefore allowed to select its
                        // default/current YouTube clients.
                        // =================================================

                        // =================================================
                        // DOWNLOAD TYPE
                        // =================================================

                        if (
                            downloadType ==
                            DownloadType.AUDIO
                        ) {

                            // =================================================
                            // AUDIO ONLY
                            // =================================================

                            Log.d(
                                TAG,
                                "Download type: AUDIO"
                            )

                            request.addOption(
                                "-f",
                                "bestaudio/best"
                            )

                            request.addOption(
                                "-x"
                            )

                            request.addOption(
                                "--audio-format",
                                "mp3"
                            )

                            request.addOption(
                                "--audio-quality",
                                "0"
                            )

                            request.addOption(
                                "--no-keep-video"
                            )

                        } else {

                            // =================================================
                            // VIDEO
                            // =================================================

                            Log.d(
                                TAG,
                                "Download type: VIDEO"
                            )

                            val formatSelector =
                                if (
                                    selectedQuality != null
                                ) {

                                    "bestvideo[height<=${selectedQuality}]+bestaudio/best[height<=${selectedQuality}]"

                                } else {

                                    "bestvideo+bestaudio/best"
                                }

                            Log.d(
                                TAG,
                                "Selected quality: $selectedQuality"
                            )

                            Log.d(
                                TAG,
                                "Format selector: $formatSelector"
                            )

                            request.addOption(
                                "-f",
                                formatSelector
                            )

                            request.addOption(
                                "--merge-output-format",
                                "mp4"
                            )

                            request.addOption(
                                "--no-keep-video"
                            )
                        }

                        // =================================================
                        // EXECUTE
                        // =================================================

                        Log.d(
                            TAG,
                            "Starting yt-dlp download"
                        )

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

                                // -----------------------------------------
                                // PROGRESS
                                // -----------------------------------------

                                onProgress(
                                    currentProgress
                                )

                                // -----------------------------------------
                                // OUTPUT
                                // -----------------------------------------

                                if (
                                    cleanOutput.isNotBlank()
                                ) {

                                    onOutput(
                                        cleanOutput
                                    )
                                }
                            }

                        // =================================================
                        // FIND NEW MEDIA FILE
                        // =================================================

                        val allowedExtensions =
                            if (
                                downloadType ==
                                DownloadType.AUDIO
                            ) {

                                setOf(
                                    "mp3",
                                    "m4a",
                                    "opus",
                                    "aac",
                                    "wav"
                                )

                            } else {

                                setOf(
                                    "mp4",
                                    "mkv",
                                    "webm"
                                )
                            }

                        val mediaFile =
                            downloadDirectory
                                .listFiles()
                                ?.filter { file ->

                                    file.isFile &&

                                            file.extension
                                                .lowercase() in
                                            allowedExtensions &&

                                            (
                                                    file.absolutePath
                                                            !in existingFiles
                                                    )

                                }
                                ?.maxByOrNull {
                                    it.lastModified()
                                }

                        mediaFile
                    }

                // =========================================================
                // DOWNLOAD COMPLETE
                // =========================================================

                if (result != null) {

                    Log.d(
                        TAG,
                        "Downloaded file: " +
                                result.absolutePath
                    )

                    onProgress(
                        100f
                    )

                    onOutput(
                        "Download complete!"
                    )

                    onComplete(
                        true
                    )

                } else {

                    Log.e(
                        TAG,
                        "Downloaded media file was not found"
                    )

                    onOutput(
                        "ERROR: Download finished, but output file was not found"
                    )

                    onComplete(
                        false
                    )
                }

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Download failed",
                    e
                )

                val errorMessage =
                    e.message
                        ?: "Unknown error"

                onOutput(
                    "ERROR: $errorMessage"
                )

                onComplete(
                    false
                )
            }
        }
    }

    // =============================================================
    // GET AVAILABLE VIDEO QUALITIES
    // =============================================================

    private fun getAvailableQualities(
        url: String,
        onQualities: (List<Int>) -> Unit,
        onError: (String) -> Unit
    ) {

        lifecycleScope.launch {

            try {

                val qualities =
                    withContext(Dispatchers.IO) {

                        // =================================================
                        // CREATE INFORMATION REQUEST
                        // =================================================

                        val request =
                            YoutubeDLRequest(url)

                        request.addOption(
                            "--dump-single-json"
                        )

                        request.addOption(
                            "--skip-download"
                        )

                        request.addOption(
                            "--no-warnings"
                        )

                        request.addOption(
                            "--force-ipv4"
                        )

                        request.addOption(
                            "--retries",
                            "10"
                        )

                        request.addOption(
                            "--socket-timeout",
                            "30"
                        )

                        // =================================================
                        // IMPORTANT
                        // =================================================
                        //
                        // Do NOT specify:
                        //
                        // --format
                        //
                        // and do NOT force:
                        //
                        // youtube:player_client=tv
                        //
                        // We only want metadata and available formats.
                        // =================================================

                        Log.d(
                            TAG,
                            "Fetching video information..."
                        )

                        // =================================================
                        // EXECUTE
                        // =================================================

                        val response =
                            YoutubeDL
                                .getInstance()
                                .execute(
                                    request
                                )

                        // =================================================
                        // GET JSON
                        // =================================================

                        val jsonText =
                            response.out

                        Log.d(
                            TAG,
                            "yt-dlp JSON length: " +
                                    jsonText.length
                        )

                        if (
                            jsonText.isBlank()
                        ) {

                            throw Exception(
                                "yt-dlp returned empty video information"
                            )
                        }

                        // =================================================
                        // PARSE JSON
                        // =================================================

                        val json =
                            JSONObject(
                                jsonText
                            )

                        // =================================================
                        // FORMATS
                        // =================================================

                        val formats =
                            json.optJSONArray(
                                "formats"
                            )

                        if (
                            formats == null
                        ) {

                            Log.e(
                                TAG,
                                "No formats array found"
                            )

                            emptyList()

                        } else {

                            val result =
                                mutableSetOf<Int>()

                            // =================================================
                            // EXAMINE FORMATS
                            // =================================================

                            for (
                            i in 0 until formats.length()
                            ) {

                                val format =
                                    formats.optJSONObject(
                                        i
                                    )
                                        ?: continue

                                // ---------------------------------------------
                                // HEIGHT
                                // ---------------------------------------------

                                val height =
                                    format.optInt(
                                        "height",
                                        0
                                    )

                                // ---------------------------------------------
                                // VIDEO CODEC
                                // ---------------------------------------------

                                val videoCodec =
                                    format.optString(
                                        "vcodec",
                                        "none"
                                    )

                                // ---------------------------------------------
                                // AUDIO/VIDEO
                                // ---------------------------------------------

                                val hasVideo =
                                    videoCodec != "none" &&
                                            videoCodec.isNotBlank()

                                // ---------------------------------------------
                                // ADD VIDEO HEIGHT
                                // ---------------------------------------------

                                if (
                                    height > 0 &&
                                    hasVideo
                                ) {

                                    result.add(
                                        height
                                    )
                                }
                            }

                            // =================================================
                            // RETURN QUALITIES
                            // =================================================

                            result
                                .filter {
                                    it >= 144
                                }
                                .sorted()
                        }
                    }

                Log.d(
                    TAG,
                    "Available video qualities: $qualities"
                )

                onQualities(
                    qualities
                )

            } catch (e: Exception) {

                Log.e(
                    TAG,
                    "Failed to get available qualities",
                    e
                )

                onError(
                    e.message
                        ?: "Unable to retrieve video qualities"
                )
            }
        }
    }
}

// =================================================================
// DOWNLOAD TYPE
// =================================================================

enum class DownloadType {

    VIDEO,
    AUDIO
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
        DownloadType,
        Int?,
        (Float) -> Unit,
        (String) -> Unit,
        (Boolean) -> Unit
    ) -> Unit,

    onGetAvailableQualities: (
        String,
        (List<Int>) -> Unit,
        (String) -> Unit
    ) -> Unit
) {

    // =============================================================
    // CONTEXT
    // =============================================================

    val context =
        LocalContext.current

    // =============================================================
    // SELECTED TAB
    // =============================================================

    var selectedTab by remember {
        mutableIntStateOf(0)
    }

    // =============================================================
    // DOWNLOADED MEDIA
    // =============================================================

    var downloadedVideos by remember {

        mutableStateOf(
            getMediaFiles(
                downloadDirectory
            )
        )
    }

    // =============================================================
    // SELECTED MEDIA
    // =============================================================

    var selectedVideo by remember {
        mutableStateOf<File?>(null)
    }

    // =============================================================
    // DOWNLOAD STATE
    // =============================================================

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

    // =============================================================
    // QUALITY STATE
    // =============================================================

    var availableQualities by remember {
        mutableStateOf<List<Int>>(
            emptyList()
        )
    }

    var selectedQuality by remember {
        mutableStateOf<Int?>(null)
    }

    var fetchingQualities by remember {
        mutableStateOf(false)
    }

    // =============================================================
    // DOWNLOAD TYPE
    // =============================================================

    var downloadType by remember {

        mutableStateOf(
            DownloadType.VIDEO
        )
    }

    // =============================================================
    // VIDEO PLAYER
    // =============================================================

    if (selectedVideo != null) {

        VideoPlayerScreen(

            videoFile =
                selectedVideo!!,

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
                    Text(
                        "YT Downloader"
                    )
                }
            )
        },

        bottomBar = {

            NavigationBar {

                NavigationBarItem(

                    selected =
                        selectedTab == 0,

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
                        Text(
                            "Download"
                        )
                    }
                )

                NavigationBarItem(

                    selected =
                        selectedTab == 1,

                    onClick = {

                        selectedTab = 1

                        downloadedVideos =
                            getMediaFiles(
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
                        Text(
                            "Downloads"
                        )
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

                    url =
                        url,

                    onUrlChange = {

                        url = it

                        availableQualities =
                            emptyList()

                        selectedQuality =
                            null

                        status =
                            if (
                                it.isBlank()
                            ) {

                                "Ready"

                            } else {

                                "Enter URL and get available qualities"
                            }
                    },

                    status =
                        status,

                    progress =
                        progress,

                    videoTitle =
                        videoTitle,

                    downloading =
                        downloading,

                    availableQualities =
                        availableQualities,

                    selectedQuality =
                        selectedQuality,

                    fetchingQualities =
                        fetchingQualities,

                    downloadType =
                        downloadType,

                    onDownloadTypeSelected = {

                        downloadType =
                            it

                        if (
                            it ==
                            DownloadType.AUDIO
                        ) {

                            selectedQuality =
                                null

                            availableQualities =
                                emptyList()

                            status =
                                "Audio-only download selected"

                        } else {

                            status =
                                "Video download selected"
                        }
                    },

                    onQualitySelected = {

                        selectedQuality =
                            it

                        status =
                            if (
                                it == null
                            ) {

                                "Best available quality selected"

                            } else {

                                "${it}p selected"
                            }
                    },

                    onFetchQualities = {

                        if (
                            url.isBlank()
                        ) {

                            status =
                                "Please enter a YouTube URL"

                            return@DownloadScreen
                        }

                        if (
                            fetchingQualities
                        ) {

                            return@DownloadScreen
                        }

                        fetchingQualities =
                            true

                        availableQualities =
                            emptyList()

                        selectedQuality =
                            null

                        status =
                            "Fetching available qualities..."

                        onGetAvailableQualities(

                            url,

                            { qualities ->

                                fetchingQualities =
                                    false

                                availableQualities =
                                    qualities

                                if (
                                    qualities.isEmpty()
                                ) {

                                    status =
                                        "No video qualities found"

                                } else {

                                    status =
                                        "Available qualities loaded"
                                }
                            },

                            { error ->

                                fetchingQualities =
                                    false

                                status =
                                    "ERROR: $error"
                            }
                        )
                    },

                    onDownload = {

                        if (
                            url.isBlank()
                        ) {

                            status =
                                "Please enter a YouTube URL"

                            return@DownloadScreen
                        }

                        if (
                            downloading
                        ) {

                            return@DownloadScreen
                        }

                        status =
                            "Starting download..."

                        progress =
                            0f

                        videoTitle =
                            ""

                        downloading =
                            true

                        onDownload(

                            url,

                            downloadType,

                            selectedQuality,

                            { currentProgress ->

                                progress =
                                    currentProgress
                            },

                            { output ->

                                if (
                                    output.isNotBlank()
                                ) {

                                    Log.d(
                                        MainActivity.TAG,
                                        "UI output: $output"
                                    )
                                }

                                // -----------------------------------------
                                // ERROR
                                // -----------------------------------------

                                if (
                                    output.startsWith(
                                        "ERROR:"
                                    )
                                ) {

                                    status =
                                        output
                                }

                                // -----------------------------------------
                                // TITLE / GENERAL OUTPUT
                                // -----------------------------------------

                                if (
                                    output.isNotBlank() &&
                                    !output.startsWith("[") &&
                                    !output.startsWith("ERROR:") &&
                                    !output.contains(
                                        "Downloading"
                                    ) &&
                                    !output.contains(
                                        "Destination:"
                                    ) &&
                                    !output.contains(
                                        "Merging"
                                    ) &&
                                    !output.contains(
                                        "Extracting"
                                    ) &&
                                    !output.contains(
                                        "Download complete"
                                    )
                                ) {

                                    videoTitle =
                                        output.trim()
                                }
                            },

                            { success ->

                                downloading =
                                    false

                                if (
                                    success
                                ) {

                                    progress =
                                        100f

                                    status =
                                        "Download complete!"

                                    downloadedVideos =
                                        getMediaFiles(
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
                            if (
                                downloadType ==
                                DownloadType.AUDIO
                            ) {

                                "Downloading audio..."

                            } else {

                                "Downloading video..."
                            }
                    },

                    onPaste = {

                        try {

                            val clipboard =
                                context.getSystemService(
                                    Context.CLIPBOARD_SERVICE
                                ) as ClipboardManager

                            if (
                                !clipboard.hasPrimaryClip()
                            ) {

                                status =
                                    "Clipboard is empty"

                                return@DownloadScreen
                            }

                            val clip =
                                clipboard.primaryClip

                            if (
                                clip == null ||
                                clip.itemCount == 0
                            ) {

                                status =
                                    "Clipboard is empty"

                                return@DownloadScreen
                            }

                            val pastedText =
                                clip
                                    .getItemAt(0)
                                    .coerceToText(
                                        context
                                    )
                                    .toString()
                                    .trim()

                            if (
                                pastedText.isBlank()
                            ) {

                                status =
                                    "Clipboard does not contain text"

                                return@DownloadScreen
                            }

                            url =
                                pastedText

                            availableQualities =
                                emptyList()

                            selectedQuality =
                                null

                            status =
                                "URL pasted from clipboard"

                        } catch (e: Exception) {

                            Log.e(
                                MainActivity.TAG,
                                "Failed to read clipboard",
                                e
                            )

                            status =
                                "Unable to read clipboard"
                        }
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
                            getMediaFiles(
                                downloadDirectory
                            )
                    },

                    onVideoClick = { file ->

                        selectedVideo =
                            file
                    }
                )
            }
        }
    }
}

// =================================================================
// DOWNLOAD SCREEN
// =================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    modifier: Modifier,
    url: String,
    onUrlChange: (String) -> Unit,
    status: String,
    progress: Float,
    videoTitle: String,
    downloading: Boolean,
    availableQualities: List<Int>,
    selectedQuality: Int?,
    fetchingQualities: Boolean,
    downloadType: DownloadType,
    onDownloadTypeSelected: (DownloadType) -> Unit,
    onQualitySelected: (Int?) -> Unit,
    onFetchQualities: () -> Unit,
    onDownload: () -> Unit,
    onPaste: () -> Unit
) {

    var qualityExpanded by remember {
        mutableStateOf(false)
    }

    var downloadTypeExpanded by remember {
        mutableStateOf(false)
    }

    Column(

        modifier =
            modifier
                .fillMaxSize()
                .padding(24.dp),

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(

            text =
                "YT Downloader",

            style =
                MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        // =========================================================
        // URL
        // =========================================================

        OutlinedTextField(

            value =
                url,

            onValueChange =
                onUrlChange,

            modifier =
                Modifier.fillMaxWidth(),

            label = {
                Text(
                    "YouTube URL"
                )
            },

            placeholder = {
                Text(
                    "Paste YouTube URL here"
                )
            },

            singleLine = true,

            enabled =
                !downloading &&
                        !fetchingQualities
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        // =========================================================
        // PASTE
        // =========================================================

        Button(

            onClick =
                onPaste,

            enabled =
                !downloading &&
                        !fetchingQualities,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Icon(

                imageVector =
                    Icons.Default.ContentPaste,

                contentDescription =
                    "Paste"
            )

            Spacer(
                modifier =
                    Modifier.size(8.dp)
            )

            Text(
                "Paste from Clipboard"
            )
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        // =========================================================
        // DOWNLOAD TYPE
        // =========================================================

        ExposedDropdownMenuBox(

            expanded =
                downloadTypeExpanded,

            onExpandedChange = {

                if (
                    !downloading &&
                    !fetchingQualities
                ) {

                    downloadTypeExpanded =
                        !downloadTypeExpanded
                }
            }
        ) {

            TextField(

                value =
                    if (
                        downloadType ==
                        DownloadType.VIDEO
                    ) {

                        "Video"

                    } else {

                        "Audio Only"
                    },

                onValueChange = {},

                readOnly = true,

                label = {
                    Text(
                        "Download Type"
                    )
                },

                trailingIcon = {

                    ExposedDropdownMenuDefaults
                        .TrailingIcon(
                            expanded =
                                downloadTypeExpanded
                        )
                },

                modifier =
                    Modifier
                        .fillMaxWidth()
                        .menuAnchor(),

                enabled =
                    !downloading &&
                            !fetchingQualities
            )

            ExposedDropdownMenu(

                expanded =
                    downloadTypeExpanded,

                onDismissRequest = {

                    downloadTypeExpanded =
                        false
                }
            ) {

                DropdownMenuItem(

                    text = {
                        Text(
                            "Video"
                        )
                    },

                    onClick = {

                        onDownloadTypeSelected(
                            DownloadType.VIDEO
                        )

                        downloadTypeExpanded =
                            false
                    }
                )

                DropdownMenuItem(

                    text = {
                        Text(
                            "Audio Only (MP3)"
                        )
                    },

                    onClick = {

                        onDownloadTypeSelected(
                            DownloadType.AUDIO
                        )

                        downloadTypeExpanded =
                            false
                    }
                )
            }
        }

        // =========================================================
        // VIDEO CONTROLS
        // =========================================================

        if (
            downloadType ==
            DownloadType.VIDEO
        ) {

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            Button(

                onClick =
                    onFetchQualities,

                enabled =
                    url.isNotBlank() &&
                            !downloading &&
                            !fetchingQualities,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(

                    if (
                        fetchingQualities
                    ) {

                        "Fetching Qualities..."

                    } else {

                        "Get Available Qualities"
                    }
                )
            }

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )

            // =====================================================
            // QUALITY DROPDOWN
            // =====================================================

            ExposedDropdownMenuBox(

                expanded =
                    qualityExpanded,

                onExpandedChange = {

                    if (
                        !fetchingQualities &&
                        !downloading &&
                        availableQualities.isNotEmpty()
                    ) {

                        qualityExpanded =
                            !qualityExpanded
                    }
                }
            ) {

                TextField(

                    value =
                        selectedQuality?.let {
                            "${it}p"
                        }
                            ?: "Best Available",

                    onValueChange = {},

                    readOnly = true,

                    label = {
                        Text(
                            "Video Quality"
                        )
                    },

                    trailingIcon = {

                        ExposedDropdownMenuDefaults
                            .TrailingIcon(
                                expanded =
                                    qualityExpanded
                            )
                    },

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .menuAnchor(),

                    enabled =
                        !downloading &&
                                !fetchingQualities &&
                                availableQualities.isNotEmpty()
                )

                ExposedDropdownMenu(

                    expanded =
                        qualityExpanded,

                    onDismissRequest = {

                        qualityExpanded =
                            false
                    }
                ) {

                    DropdownMenuItem(

                        text = {
                            Text(
                                "Best Available"
                            )
                        },

                        onClick = {

                            onQualitySelected(
                                null
                            )

                            qualityExpanded =
                                false
                        }
                    )

                    availableQualities
                        .sortedDescending()
                        .forEach { quality ->

                            DropdownMenuItem(

                                text = {

                                    Text(
                                        "${quality}p"
                                    )
                                },

                                onClick = {

                                    onQualitySelected(
                                        quality
                                    )

                                    qualityExpanded =
                                        false
                                }
                            )
                        }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        // =========================================================
        // DOWNLOAD BUTTON
        // =========================================================

        Button(

            enabled =
                !downloading &&
                        !fetchingQualities,

            onClick =
                onDownload,

            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(

                if (
                    downloading
                ) {

                    "Downloading..."

                } else if (
                    downloadType ==
                    DownloadType.AUDIO
                ) {

                    "Download Audio"

                } else {

                    "Download Video"
                }
            )
        }

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        // =========================================================
        // TITLE
        // =========================================================

        if (
            videoTitle.isNotBlank()
        ) {

            Text(

                text =
                    "Title: $videoTitle",

                style =
                    MaterialTheme.typography.titleMedium,

                maxLines = 2,

                overflow =
                    TextOverflow.Ellipsis
            )

            Spacer(
                modifier =
                    Modifier.height(12.dp)
            )
        }

        // =========================================================
        // STATUS
        // =========================================================

        Text(

            text =
                "Status: $status",

            style =
                MaterialTheme.typography.bodyLarge,

            maxLines = 3,

            overflow =
                TextOverflow.Ellipsis
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        // =========================================================
        // PROGRESS
        // =========================================================

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

        Row(

            modifier =
                Modifier.fillMaxWidth(),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(

                text =
                    "Downloaded Media",

                style =
                    MaterialTheme.typography.headlineSmall,

                modifier =
                    Modifier.weight(1f)
            )

            IconButton(
                onClick =
                    onRefresh
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

        if (
            videos.isEmpty()
        ) {

            Column(

                modifier =
                    Modifier.fillMaxSize(),

                verticalArrangement =
                    Arrangement.Center,

                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    "No downloaded media"
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    "Downloaded videos and audio will appear here."
                )
            }

        } else {

            LazyColumn {

                items(

                    items =
                        videos,

                    key = {
                        it.absolutePath
                    }

                ) { file ->

                    VideoListItem(

                        file =
                            file,

                        onClick = {

                            onVideoClick(
                                file
                            )
                        }
                    )
                }
            }
        }
    }
}

// =================================================================
// MEDIA LIST ITEM
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
                        "${file.extension.uppercase()} • " +
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
// VIDEO / AUDIO PLAYER
// =================================================================

@Composable
fun VideoPlayerScreen(
    videoFile: File,
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    val exoPlayer =
        remember(videoFile.absolutePath) {

            ExoPlayer
                .Builder(context)
                .build()
                .apply {

                    val mediaItem =
                        MediaItem.fromUri(
                            Uri.fromFile(
                                videoFile
                            )
                        )

                    setMediaItem(
                        mediaItem
                    )

                    prepare()

                    playWhenReady =
                        true
                }
        }

    DisposableEffect(
        exoPlayer
    ) {

        onDispose {
            exoPlayer.release()
        }
    }

    Column(

        modifier =
            Modifier.fillMaxSize()
    ) {

        Button(

            onClick =
                onBack,

            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
        ) {

            Text(
                "Back to Downloads"
            )
        }

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

        AndroidView(

            factory = { ctx ->

                PlayerView(
                    ctx
                ).apply {

                    player =
                        exoPlayer

                    useController =
                        true
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
// GET MEDIA FILES
// =================================================================

fun getMediaFiles(
    directory: File
): List<File> {

    if (
        !directory.exists()
    ) {

        return emptyList()
    }

    val supportedExtensions =
        setOf(
            "mp4",
            "mkv",
            "webm",
            "mp3",
            "m4a",
            "opus",
            "aac",
            "wav"
        )

    return directory
        .listFiles()
        ?.filter {

            it.isFile &&
                    it.extension
                        .lowercase() in
                    supportedExtensions
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

    if (
        bytes < 1024
    ) {

        return "$bytes B"
    }

    if (
        bytes < 1024 * 1024
    ) {

        return String.format(
            "%.1f KB",
            bytes / 1024.0
        )
    }

    if (
        bytes <
        1024L *
        1024L *
        1024L
    ) {

        return String.format(
            "%.1f MB",
            bytes /
                    (
                            1024.0 *
                                    1024.0
                            )
        )
    }

    return String.format(
        "%.2f GB",
        bytes /
                (
                        1024.0 *
                                1024.0 *
                                1024.0
                        )
    )
}

