package com.example.ytdownloader.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ytdownloader.model.DownloadType

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
    var qualityExpanded by remember { mutableStateOf(false) }
    var downloadTypeExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
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
            enabled = !downloading && !fetchingQualities
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = onPaste,
            enabled = !downloading && !fetchingQualities,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.ContentPaste, contentDescription = "Paste")
            Spacer(Modifier.size(8.dp))
            Text("Paste from Clipboard")
        }

        Spacer(Modifier.height(16.dp))

        // Download type dropdown
        ExposedDropdownMenuBox(
            expanded = downloadTypeExpanded,
            onExpandedChange = {
                if (!downloading && !fetchingQualities) downloadTypeExpanded = !downloadTypeExpanded
            }
        ) {
            TextField(
                value = if (downloadType == DownloadType.VIDEO) "Video" else "Audio Only",
                onValueChange = {},
                readOnly = true,
                label = { Text("Download Type") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = downloadTypeExpanded) },
                modifier = Modifier.fillMaxWidth().menuAnchor(),
                enabled = !downloading && !fetchingQualities
            )

            ExposedDropdownMenu(
                expanded = downloadTypeExpanded,
                onDismissRequest = { downloadTypeExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Video") },
                    onClick = { onDownloadTypeSelected(DownloadType.VIDEO); downloadTypeExpanded = false }
                )
                DropdownMenuItem(
                    text = { Text("Audio Only (MP3)") },
                    onClick = { onDownloadTypeSelected(DownloadType.AUDIO); downloadTypeExpanded = false }
                )
            }
        }

        if (downloadType == DownloadType.VIDEO) {
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onFetchQualities,
                enabled = url.isNotBlank() && !downloading && !fetchingQualities,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (fetchingQualities) "Fetching Qualities..." else "Get Available Qualities")
            }

            Spacer(Modifier.height(12.dp))

            ExposedDropdownMenuBox(
                expanded = qualityExpanded,
                onExpandedChange = {
                    if (!fetchingQualities && !downloading && availableQualities.isNotEmpty()) {
                        qualityExpanded = !qualityExpanded
                    }
                }
            ) {
                TextField(
                    value = selectedQuality?.let { "${it}p" } ?: "Best Available",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Video Quality") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = qualityExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    enabled = !downloading && !fetchingQualities && availableQualities.isNotEmpty()
                )

                ExposedDropdownMenu(
                    expanded = qualityExpanded,
                    onDismissRequest = { qualityExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Best Available") },
                        onClick = { onQualitySelected(null); qualityExpanded = false }
                    )
                    availableQualities.sortedDescending().forEach { quality ->
                        DropdownMenuItem(
                            text = { Text("${quality}p") },
                            onClick = { onQualitySelected(quality); qualityExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            enabled = !downloading && !fetchingQualities,
            onClick = onDownload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    downloading -> "Downloading..."
                    downloadType == DownloadType.AUDIO -> "Download Audio"
                    else -> "Download Video"
                }
            )
        }

        Spacer(Modifier.height(20.dp))

        if (videoTitle.isNotBlank()) {
            Text(
                text = "Title: $videoTitle",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(
            text = "Status: $status",
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(12.dp))
        Text(text = "Progress: ${progress.toInt()}%")
    }
}