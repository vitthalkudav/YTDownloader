package com.example.ytdownloader

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.ui.theme.YTDownloaderTheme
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * Initialize yt-dlp
         */
        try {

            YoutubeDL.getInstance().init(this)

            Log.d(
                "YTDownloader",
                "yt-dlp environment initialized successfully"
            )

            /*
             * Check FFmpeg
             */
            val nativeDir = applicationInfo.nativeLibraryDir

            val ffmpegFile = File(
                nativeDir,
                "libffmpeg.so"
            )

            Log.d(
                "YTDownloader",
                "Native library directory: $nativeDir"
            )

            Log.d(
                "YTDownloader",
                "FFmpeg exists: ${ffmpegFile.exists()}"
            )

            Log.d(
                "YTDownloader",
                "FFmpeg executable: ${ffmpegFile.canExecute()}"
            )

            Log.d(
                "YTDownloader",
                "FFmpeg path: ${ffmpegFile.absolutePath}"
            )

            /*
             * Test FFmpeg
             */
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
                    "YTDownloader",
                    "FFmpeg test exit code: $exitCode"
                )

                Log.d(
                    "YTDownloader",
                    "FFmpeg test output: $output"
                )

            } catch (e: Exception) {

                Log.e(
                    "YTDownloader",
                    "FFmpeg test failed",
                    e
                )
            }

            /*
             * Update yt-dlp
             */
            Thread {

                try {

                    val result =
                        YoutubeDL.getInstance().updateYoutubeDL(
                            this,
                            YoutubeDL.UpdateChannel.STABLE
                        )

                    Log.d(
                        "YTDownloader",
                        "yt-dlp update result: $result"
                    )

                } catch (e: Exception) {

                    Log.e(
                        "YTDownloader",
                        "yt-dlp update failed",
                        e
                    )
                }

            }.start()

        } catch (e: YoutubeDLException) {

            Log.e(
                "YTDownloader",
                "yt-dlp initialization failed",
                e
            )
        }

        /*
         * Compose UI
         */
        setContent {

            YTDownloaderTheme {

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    DownloaderScreen()
                }
            }
        }
    }
}


@Composable
fun DownloaderScreen() {

    /*
     * YouTube URL
     */
    var url by remember {
        mutableStateOf("")
    }

    /*
     * Status text
     */
    var status by remember {
        mutableStateOf("Ready")
    }

    /*
     * Download progress
     */
    var progress by remember {
        mutableStateOf(0f)
    }

    /*
     * Video title
     */
    var videoTitle by remember {
        mutableStateOf("")
    }

    /*
     * Prevent multiple downloads
     */
    var isDownloading by remember {
        mutableStateOf(false)
    }

    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),

        verticalArrangement = Arrangement.Center
    ) {

        /*
         * Title
         */
        Text(
            text = "YT Downloader",
            style = MaterialTheme.typography.headlineLarge
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        /*
         * URL input
         */
        OutlinedTextField(

            value = url,

            onValueChange = {
                url = it
            },

            modifier = Modifier.fillMaxWidth(),

            label = {
                Text("YouTube URL")
            },

            placeholder = {
                Text("Paste YouTube URL here")
            },

            singleLine = true,

            enabled = !isDownloading
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        /*
         * Download button
         */
        Button(

            enabled = !isDownloading,

            onClick = {

                /*
                 * Validate URL
                 */
                if (url.isBlank()) {

                    status = "Please enter a YouTube URL"

                    return@Button
                }

                /*
                 * Reset values
                 */
                status = "Starting download..."

                progress = 0f

                videoTitle = ""

                isDownloading = true

                /*
                 * Start background operation
                 */
                scope.launch {

                    try {

                        val result = withContext(Dispatchers.IO) {

                            /*
                             * Downloads/YTDownloader
                             */
                            val downloadDirectory = File(
                                Environment
                                    .getExternalStoragePublicDirectory(
                                        Environment.DIRECTORY_DOWNLOADS
                                    ),
                                "YTDownloader"
                            )

                            /*
                             * Create directory
                             */
                            if (!downloadDirectory.exists()) {

                                downloadDirectory.mkdirs()
                            }

                            Log.d(
                                "YTDownloader",
                                "Download directory: " +
                                        downloadDirectory.absolutePath
                            )

                            /*
                             * yt-dlp request
                             */
                            val request =
                                YoutubeDLRequest(url)

                            /*
                             * Output filename
                             */
                            request.addOption(
                                "-o",
                                "${downloadDirectory.absolutePath}/%(title)s.%(ext)s"
                            )

                            /*
                             * Best video + best audio.
                             *
                             * Falls back to a combined
                             * format if necessary.
                             */
                            request.addOption(
                                "-f",
                                "bv*+ba/b"
                            )

                            /*
                             * Merge video and audio
                             * into MP4.
                             */
                            request.addOption(
                                "--merge-output-format",
                                "mp4"
                            )

                            /*
                             * Do not keep separate
                             * video/audio files.
                             */
                            request.addOption(
                                "--no-keep-video"
                            )

                            /*
                             * Execute yt-dlp
                             */
                            YoutubeDL
                                .getInstance()
                                .execute(
                                    request
                                ) { currentProgress,
                                    etaInSeconds,
                                    output ->

                                    /*
                                     * Convert output safely
                                     * to String.
                                     *
                                     * This avoids the
                                     * unresolved reference
                                     * problem with trim().
                                     */
                                    val cleanOutput =
                                        output
                                            .toString()
                                            .trim()

                                    /*
                                     * Log complete yt-dlp
                                     * output.
                                     */
                                    Log.d(
                                        "YTDownloader",
                                        "yt-dlp: $cleanOutput"
                                    )

                                    /*
                                     * Update progress.
                                     */
                                    progress =
                                        currentProgress

                                    /*
                                     * Try to extract title.
                                     *
                                     * yt-dlp commonly outputs:
                                     *
                                     * [download] Destination:
                                     * /path/VIDEO_TITLE.webm
                                     *
                                     * or:
                                     *
                                     * [Merger] Merging formats into
                                     * "/path/VIDEO_TITLE.mp4"
                                     */

                                    if (
                                        cleanOutput.contains(
                                            "[download] Destination:"
                                        )
                                    ) {

                                        val destination =
                                            cleanOutput.substringAfter(
                                                "[download] Destination:"
                                            ).trim()

                                        val fileName =
                                            File(destination).name

                                        /*
                                         * Remove extension.
                                         */
                                        val title =
                                            fileName.substringBeforeLast(
                                                "."
                                            )

                                        if (title.isNotBlank()) {

                                            videoTitle = title
                                        }
                                    }

                                    /*
                                     * Also handle Merger output.
                                     */
                                    if (
                                        cleanOutput.contains(
                                            "[Merger] Merging formats into"
                                        )
                                    ) {

                                        val mergerPath =
                                            cleanOutput
                                                .substringAfter(
                                                    "[Merger] Merging formats into"
                                                )
                                                .trim()
                                                .removeSurrounding("\"")

                                        val fileName =
                                            File(mergerPath).name

                                        val title =
                                            fileName.substringBeforeLast(
                                                "."
                                            )

                                        if (title.isNotBlank()) {

                                            videoTitle = title
                                        }
                                    }

                                    /*
                                     * Update UI status.
                                     */
                                    status =
                                        if (currentProgress >= 100f) {

                                            "Finalizing..."

                                        } else {

                                            "Downloading: " +
                                                    "${currentProgress.toInt()}%"
                                        }
                                }

                            /*
                             * Find the resulting MP4.
                             */
                            val files =
                                downloadDirectory
                                    .listFiles()

                            val mp4File =
                                files
                                    ?.filter {
                                        it.isFile &&
                                                it.extension
                                                    .equals(
                                                        "mp4",
                                                        ignoreCase = true
                                                    )
                                    }
                                    ?.maxByOrNull {
                                        it.lastModified()
                                    }

                            /*
                             * Return information about
                             * the downloaded file.
                             */
                            Pair(
                                downloadDirectory.absolutePath,
                                mp4File
                            )
                        }

                        /*
                         * Get returned values.
                         */
                        val downloadPath =
                            result.first

                        val mp4File =
                            result.second

                        /*
                         * Download completed.
                         */
                        progress = 100f

                        isDownloading = false

                        /*
                         * If an MP4 was found,
                         * use its filename as the
                         * most reliable title.
                         */
                        if (mp4File != null) {

                            val fileName =
                                mp4File.name

                            val title =
                                fileName.substringBeforeLast(
                                    "."
                                )

                            if (title.isNotBlank()) {

                                videoTitle = title
                            }

                            status =
                                "Download complete!"

                            Log.d(
                                "YTDownloader",
                                "Downloaded file: " +
                                        mp4File.absolutePath
                            )

                        } else {

                            /*
                             * yt-dlp completed but
                             * no MP4 was found.
                             */
                            status =
                                "Download finished, but MP4 file was not found"

                            Log.e(
                                "YTDownloader",
                                "No MP4 file found in: $downloadPath"
                            )
                        }

                        Log.d(
                            "YTDownloader",
                            "Files saved to: $downloadPath"
                        )

                    } catch (e: Exception) {

                        /*
                         * Download failed.
                         */
                        isDownloading = false

                        progress = 0f

                        status =
                            "Download failed: ${e.message}"

                        Log.e(
                            "YTDownloader",
                            "Download failed",
                            e
                        )
                    }
                }
            },

            modifier = Modifier.fillMaxWidth()
        ) {

            Text(
                text =
                    if (isDownloading) {
                        "Downloading..."
                    } else {
                        "Download Video"
                    }
            )
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        /*
         * Video title
         */
        if (videoTitle.isNotBlank()) {

            Text(
                text = "Video: $videoTitle",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        /*
         * Status
         */
        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyLarge
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        /*
         * Progress
         */
        Text(
            text = "Progress: ${progress.toInt()}%"
        )
    }
}