package com.example.ytdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.download.DownloadTask
import com.example.ytdownloader.model.DownloadType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadScreen(
    modifier: Modifier,
    url: String,
    onUrlChange: (String) -> Unit,
    status: String,
    availableQualities: List<Int>,
    selectedQuality: Int?,
    fetchingQualities: Boolean,
    downloadType: DownloadType,
    onDownloadTypeSelected: (DownloadType) -> Unit,
    onQualitySelected: (Int?) -> Unit,
    onFetchQualities: () -> Unit,
    onDownload: () -> Unit,
    onPaste: () -> Unit,
    activeTasks: List<DownloadTask>,
    onCancelTask: (String) -> Unit,
    onDismissTask: (String) -> Unit
) {
    var qualityExpanded by remember { mutableStateOf(false) }
    var downloadTypeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text("YT Downloader", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("YouTube URL") },
            placeholder = { Text("Paste YouTube URL here") },
            singleLine = true,
            enabled = !fetchingQualities
        )

        Spacer(Modifier.height(8.dp))

        Button(onClick = onPaste, enabled = !fetchingQualities, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
            Spacer(Modifier.size(8.dp))
            Text("Paste from Clipboard")
        }

        Spacer(Modifier.height(16.dp))

        ExposedDropdownMenuBox(
            expanded = downloadTypeExpanded,
            onExpandedChange = { if (!fetchingQualities) downloadTypeExpanded = !downloadTypeExpanded }
        ) {
            TextField(
                value = if (downloadType == DownloadType.VIDEO) "Video" else "Audio Only",
                onValueChange = {},
                readOnly = true,
                label = { Text("Download Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = downloadTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                enabled = !fetchingQualities
            )

            ExposedDropdownMenu(expanded = downloadTypeExpanded, onDismissRequest = { downloadTypeExpanded = false }) {
                DropdownMenuItem(text = { Text("Video") }, onClick = { onDownloadTypeSelected(DownloadType.VIDEO); downloadTypeExpanded = false })
                DropdownMenuItem(text = { Text("Audio Only (MP3)") }, onClick = { onDownloadTypeSelected(DownloadType.AUDIO); downloadTypeExpanded = false })
            }
        }

        if (downloadType == DownloadType.VIDEO) {
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onFetchQualities,
                enabled = url.isNotBlank() && !fetchingQualities,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (fetchingQualities) "Fetching Qualities..." else "Get Available Qualities")
            }

            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = qualityExpanded,
                onExpandedChange = { if (!fetchingQualities && availableQualities.isNotEmpty()) qualityExpanded = !qualityExpanded }
            ) {
                TextField(
                    value = selectedQuality?.let { "${it}p" } ?: "Best Available",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Video Quality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    enabled = !fetchingQualities && availableQualities.isNotEmpty()
                )

                ExposedDropdownMenu(expanded = qualityExpanded, onDismissRequest = { qualityExpanded = false }) {
                    DropdownMenuItem(text = { Text("Best Available") }, onClick = { onQualitySelected(null); qualityExpanded = false })
                    availableQualities.sortedDescending().forEach { quality ->
                        DropdownMenuItem(text = { Text("${quality}p") }, onClick = { onQualitySelected(quality); qualityExpanded = false })
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            enabled = url.isNotBlank() && !fetchingQualities,
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (downloadType == DownloadType.AUDIO) "Download Audio" else "Download Video")
        }

        Spacer(Modifier.height(16.dp))

        Text(text = "Status: $status", style = MaterialTheme.typography.bodyLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)

        if (activeTasks.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Downloads", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            activeTasks.forEach { task ->
                DownloadTaskItem(
                    task = task,
                    onCancel = { onCancelTask(task.id) },
                    onDismiss = { onDismissTask(task.id) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}