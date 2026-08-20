package com.example.ytdownloader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.util.MediaUtils
import java.io.File

@Composable
fun DownloadsScreen(
    modifier: Modifier,
    videos: List<File>,
    onRefresh: () -> Unit,
    onVideoClick: (File) -> Unit
) {
    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloaded Media",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        Spacer(Modifier.height(8.dp))

        if (videos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("No downloaded media")
                Spacer(Modifier.height(8.dp))
                Text("Downloaded videos and audio will appear here.")
            }
        } else {
            LazyColumn {
                items(items = videos, key = { it.absolutePath }) { file ->
                    VideoListItem(file = file, onClick = { onVideoClick(file) })
                }
            }
        }
    }
}

@Composable
fun VideoListItem(file: File, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(40.dp))
            Spacer(Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name.substringBeforeLast("."),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${file.extension.uppercase()} • ${MediaUtils.formatFileSize(file.length())}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}