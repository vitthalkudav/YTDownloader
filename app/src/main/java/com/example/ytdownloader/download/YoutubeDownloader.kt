package com.example.ytdownloader.download

import android.content.Context
import android.util.Log
import com.example.ytdownloader.model.DownloadType
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

object YoutubeDownloader {

    private const val TAG = "YoutubeDownloader"
    private const val TEMP_ROOT_NAME = ".ytd_tmp"

    // =============================================================
    // SETUP
    // =============================================================

    fun initialize(context: Context) {
        try {
            YoutubeDL.getInstance().init(context)
            Log.d(TAG, "yt-dlp environment initialized successfully")
            Log.d(TAG, "Native library directory: ${context.applicationInfo.nativeLibraryDir}")
        } catch (e: YoutubeDLException) {
            Log.e(TAG, "yt-dlp initialization failed", e)
        }
    }

    fun updateInBackground(context: Context) {
        Thread {
            try {
                val result = YoutubeDL.getInstance()
                    .updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
                Log.d(TAG, "yt-dlp update result: $result")
            } catch (e: Exception) {
                Log.e(TAG, "yt-dlp update failed", e)
            }
        }.start()
    }

    /** Removes any orphaned per-download temp folders left behind by a crash or force-kill. */
    fun cleanupTempFiles(downloadDirectory: File) {
        val tempRoot = File(downloadDirectory, TEMP_ROOT_NAME)
        if (tempRoot.exists()) tempRoot.deleteRecursively()
    }

    // =============================================================
    // DOWNLOAD
    // =============================================================

    /**
     * Downloads [url] into its own private temp folder (so concurrent downloads never collide),
     * then moves the finished file into [downloadDirectory]. [processId] must be unique per call —
     * it's what lets [com.yausername.youtubedl_android.YoutubeDL.destroyProcessById] cancel this
     * specific download without touching any others running at the same time.
     */
    suspend fun downloadMedia(
        downloadDirectory: File,
        url: String,
        downloadType: DownloadType,
        selectedQuality: Int?,
        processId: String,
        onProgress: (Float) -> Unit,
        onOutput: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            throw Exception("Unable to create download directory")
        }

        val tempDir = File(downloadDirectory, "$TEMP_ROOT_NAME/$processId")
        tempDir.mkdirs()

        try {
            val request = YoutubeDLRequest(url)
            request.addOption("-o", "${tempDir.absolutePath}/%(title)s.%(ext)s")
            request.addOption("--force-ipv4")
            request.addOption("--retries", "10")
            request.addOption("--fragment-retries", "10")
            request.addOption("--socket-timeout", "30")
            request.addOption("--retry-sleep", "1")

            if (downloadType == DownloadType.AUDIO) {
                Log.d(TAG, "Download type: AUDIO ($processId)")
                request.addOption("-f", "bestaudio/best")
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
                request.addOption("--audio-quality", "0")
                request.addOption("--no-keep-video")
            } else {
                Log.d(TAG, "Download type: VIDEO ($processId)")
                val formatSelector = if (selectedQuality != null)
                    "bestvideo[height<=${selectedQuality}]+bestaudio/best[height<=${selectedQuality}]"
                else
                    "bestvideo+bestaudio/best"

                request.addOption("-f", formatSelector)
                request.addOption("--merge-output-format", "mp4")
                request.addOption("--no-keep-video")
            }

            Log.d(TAG, "Starting yt-dlp download ($processId)")

            YoutubeDL.getInstance().execute(
                request = request,
                processId = processId,
                callback = { currentProgress, _, output ->
                    val cleanOutput = output.toString().trim()
                    onProgress(currentProgress)
                    if (cleanOutput.isNotBlank()) onOutput(cleanOutput)
                }
            )

            val allowedExtensions = if (downloadType == DownloadType.AUDIO)
                setOf("mp3", "m4a", "opus", "aac", "wav")
            else
                setOf("mp4", "mkv", "webm")

            val downloadedFile = tempDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in allowedExtensions }
                ?.maxByOrNull { it.lastModified() }
                ?: throw Exception("Download finished, but output file was not found")

            val destination = resolveUniqueDestination(downloadDirectory, downloadedFile.name)
            if (!downloadedFile.renameTo(destination)) {
                downloadedFile.copyTo(destination, overwrite = false)
            }

            destination
        } finally {
            tempDir.deleteRecursively()
        }
    }

    /** Appends " (1)", " (2)", etc. if a file with the same name already exists. */
    private fun resolveUniqueDestination(directory: File, fileName: String): File {
        var candidate = File(directory, fileName)
        if (!candidate.exists()) return candidate

        val base = fileName.substringBeforeLast(".", fileName)
        val ext = fileName.substringAfterLast(".", "")
        var counter = 1
        while (candidate.exists()) {
            candidate = File(directory, if (ext.isNotEmpty()) "$base ($counter).$ext" else "$base ($counter)")
            counter++
        }
        return candidate
    }

    // =============================================================
    // QUALITIES
    // =============================================================

    suspend fun getAvailableQualities(url: String): List<Int> = withContext(Dispatchers.IO) {
        val request = YoutubeDLRequest(url)
        request.addOption("--dump-single-json")
        request.addOption("--skip-download")
        request.addOption("--no-warnings")
        request.addOption("--force-ipv4")
        request.addOption("--retries", "10")
        request.addOption("--socket-timeout", "30")

        Log.d(TAG, "Fetching video information...")

        val response = YoutubeDL.getInstance().execute(request)
        val jsonText = response.out
        if (jsonText.isBlank()) throw Exception("yt-dlp returned empty video information")

        val json = JSONObject(jsonText)
        val formats = json.optJSONArray("formats") ?: return@withContext emptyList()

        val result = mutableSetOf<Int>()
        for (i in 0 until formats.length()) {
            val format = formats.optJSONObject(i) ?: continue
            val height = format.optInt("height", 0)
            val videoCodec = format.optString("vcodec", "none")
            val hasVideo = videoCodec != "none" && videoCodec.isNotBlank()
            if (height > 0 && hasVideo) result.add(height)
        }

        result.filter { it >= 144 }.sorted()
    }
}