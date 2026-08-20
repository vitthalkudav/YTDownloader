package com.example.ytdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.download.DownloadStatus
import com.example.ytdownloader.download.DownloadTask

@Composable
fun DownloadTaskItem(
    task: DownloadTask,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title.ifBlank { task.url },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (task.status == DownloadStatus.RUNNING) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel download")
                    }
                } else {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }

            Spacer(Modifier.height(4.dp))

            when (task.status) {
                DownloadStatus.RUNNING -> {
                    LinearProgressIndicator(
                        progress = task.progress / 100f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${task.downloadType.name.lowercase().replaceFirstChar { it.uppercase() }} • ${task.progress.toInt()}%",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                DownloadStatus.COMPLETE -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Complete", style = MaterialTheme.typography.bodySmall)
                }
                DownloadStatus.ERROR -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = task.errorMessage ?: "Download failed",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DownloadStatus.CANCELLED -> Text("Cancelled", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}