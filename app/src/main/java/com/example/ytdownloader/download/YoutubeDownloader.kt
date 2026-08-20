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

    // =============================================================
    // DOWNLOAD
    // =============================================================

    /** Downloads the given URL and returns the resulting media file, or throws on failure. */
    suspend fun downloadMedia(
        downloadDirectory: File,
        url: String,
        downloadType: DownloadType,
        selectedQuality: Int?,
        onProgress: (Float) -> Unit,
        onOutput: (String) -> Unit
    ): File = withContext(Dispatchers.IO) {

        if (!downloadDirectory.exists() && !downloadDirectory.mkdirs()) {
            throw Exception("Unable to create download directory")
        }

        val existingFiles: Set<String> =
            downloadDirectory.listFiles()?.map { it.absolutePath }?.toSet() ?: emptySet()

        val request = YoutubeDLRequest(url)
        request.addOption("-o", "${downloadDirectory.absolutePath}/%(title)s.%(ext)s")
        request.addOption("--force-ipv4")
        request.addOption("--retries", "10")
        request.addOption("--fragment-retries", "10")
        request.addOption("--socket-timeout", "30")
        request.addOption("--retry-sleep", "1")

        if (downloadType == DownloadType.AUDIO) {
            Log.d(TAG, "Download type: AUDIO")
            request.addOption("-f", "bestaudio/best")
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", "0")
            request.addOption("--no-keep-video")
        } else {
            Log.d(TAG, "Download type: VIDEO")
            val formatSelector = if (selectedQuality != null)
                "bestvideo[height<=${selectedQuality}]+bestaudio/best[height<=${selectedQuality}]"
            else
                "bestvideo+bestaudio/best"

            Log.d(TAG, "Selected quality: $selectedQuality")
            Log.d(TAG, "Format selector: $formatSelector")

            request.addOption("-f", formatSelector)
            request.addOption("--merge-output-format", "mp4")
            request.addOption("--no-keep-video")
        }

        Log.d(TAG, "Starting yt-dlp download")

        YoutubeDL.getInstance().execute(request) { currentProgress, _, output ->
            val cleanOutput = output.toString().trim()
            Log.d(TAG, "yt-dlp: $cleanOutput")
            onProgress(currentProgress)
            if (cleanOutput.isNotBlank()) onOutput(cleanOutput)
        }

        val allowedExtensions = if (downloadType == DownloadType.AUDIO)
            setOf("mp3", "m4a", "opus", "aac", "wav")
        else
            setOf("mp4", "mkv", "webm")

        val mediaFile = downloadDirectory.listFiles()
            ?.filter { file ->
                file.isFile &&
                        file.extension.lowercase() in allowedExtensions &&
                        file.absolutePath !in existingFiles
            }
            ?.maxByOrNull { it.lastModified() }

        mediaFile ?: throw Exception("Download finished, but output file was not found")
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
        Log.d(TAG, "yt-dlp JSON length: ${jsonText.length}")

        if (jsonText.isBlank()) {
            throw Exception("yt-dlp returned empty video information")
        }

        val json = JSONObject(jsonText)
        val formats = json.optJSONArray("formats")

        if (formats == null) {
            Log.e(TAG, "No formats array found")
            return@withContext emptyList()
        }

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