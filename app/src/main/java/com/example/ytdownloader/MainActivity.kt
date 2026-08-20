package com.example.ytdownloader

import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.example.ytdownloader.download.YoutubeDownloader
import com.example.ytdownloader.model.DownloadType
import com.example.ytdownloader.ui.DownloaderApp
import com.example.ytdownloader.ui.theme.YTDownloaderTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {

    companion object {
        const val TAG = "YTDownloader"
    }

    private lateinit var downloadDirectory: File

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        downloadDirectory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "YTDownloader"
        )
        if (!downloadDirectory.exists()) downloadDirectory.mkdirs()
        Log.d(TAG, "Download directory: ${downloadDirectory.absolutePath}")

        YoutubeDownloader.initialize(this)
        YoutubeDownloader.updateInBackground(this)

        setContent {
            YTDownloaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DownloaderApp(
                        downloadDirectory = downloadDirectory,
                        onDownload = { url, downloadType, selectedQuality, onProgress, onOutput, onComplete ->
                            downloadMedia(url, downloadType, selectedQuality, onProgress, onOutput, onComplete)
                        },
                        onGetAvailableQualities = { url, onQualities, onError ->
                            getAvailableQualities(url, onQualities, onError)
                        }
                    )
                }
            }
        }
    }

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
                val file = YoutubeDownloader.downloadMedia(
                    downloadDirectory, url, downloadType, selectedQuality, onProgress, onOutput
                )
                Log.d(TAG, "Downloaded file: ${file.absolutePath}")
                onProgress(100f)
                onOutput("Download complete!")
                onComplete(true)
            } catch (e: Exception) {
                Log.e(TAG, "Download failed", e)
                onOutput("ERROR: ${e.message ?: "Unknown error"}")
                onComplete(false)
            }
        }
    }

    private fun getAvailableQualities(
        url: String,
        onQualities: (List<Int>) -> Unit,
        onError: (String) -> Unit
    ) {
        lifecycleScope.launch {
            try {
                val qualities = YoutubeDownloader.getAvailableQualities(url)
                Log.d(TAG, "Available video qualities: $qualities")
                onQualities(qualities)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get available qualities", e)
                onError(e.message ?: "Unable to retrieve video qualities")
            }
        }
    }
}