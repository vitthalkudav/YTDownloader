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

        // Clear leftover per-download temp folders from a previous crash or force-kill.
        YoutubeDownloader.cleanupTempFiles(downloadDirectory)

        YoutubeDownloader.initialize(this)
        YoutubeDownloader.updateInBackground(this)

        setContent {
            YTDownloaderTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DownloaderApp(
                        downloadDirectory = downloadDirectory,
                        onGetAvailableQualities = { url, onQualities, onError ->
                            getAvailableQualities(url, onQualities, onError)
                        }
                    )
                }
            }
        }
    }

    private fun getAvailableQualities(url: String, onQualities: (List<Int>) -> Unit, onError: (String) -> Unit) {
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