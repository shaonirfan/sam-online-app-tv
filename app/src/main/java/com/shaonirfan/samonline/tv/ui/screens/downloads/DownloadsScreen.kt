package com.shaonirfan.samonline.tv.ui.screens.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.data.download.DownloadStatus
import com.shaonirfan.samonline.tv.data.download.DownloadTask
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.PrimaryActionButton
import com.shaonirfan.samonline.tv.ui.components.ProgressStrip
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import com.shaonirfan.samonline.tv.ui.components.formatBytes
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.navigation.playSingle
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors

@Composable
fun DownloadsScreen(nav: NavController) {
    val manager = LocalAppContainer.current.downloads
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val focus = rememberFocusMemory("downloads")
    var pendingDelete by remember { mutableStateOf<DownloadTask?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Downloads",
            style = MaterialTheme.typography.headlineMedium,
            color = SamColors.TextPrimary,
            modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = Dimens.SafeVertical),
        )
        Text(
            "OK: play / pause / resume  ·  Hold OK: delete  ·  Saved to Movies/SAM Online TV",
            style = MaterialTheme.typography.bodyMedium,
            color = SamColors.TextMuted,
            modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 4.dp, bottom = 12.dp),
        )
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No downloads yet. Open a movie and choose Download, or hold OK on an episode.",
                    style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(tasks, key = { _, it -> it.id }) { index, task ->
                    DownloadRow(
                        task = task,
                        onClick = {
                            when (task.status) {
                                DownloadStatus.COMPLETED -> nav.playSingle(task.localUri.ifBlank { task.url }, task.title, null)
                                DownloadStatus.DOWNLOADING -> manager.pause(task.id)
                                else -> manager.resume(task.id)
                            }
                        },
                        onLongClick = { pendingDelete = task },
                        modifier = Modifier
                            .rememberFocus(focus, task.id)
                            .then(if (index == 0) Modifier.initialFocus(focus) else Modifier),
                    )
                }
            }
        }
    }
    FocusRestoreEffect(focus, ready = tasks.isNotEmpty())

    pendingDelete?.let { task ->
        ConfirmDeleteDialog(
            title = task.title,
            onConfirm = { manager.delete(task.id); pendingDelete = null },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun DownloadRow(task: DownloadTask, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier) {
    val (icon, status) = when (task.status) {
        DownloadStatus.QUEUED -> Icons.Filled.Schedule to "Queued"
        DownloadStatus.DOWNLOADING -> Icons.Filled.Downloading to "Downloading ${(task.progress * 100).toInt()}%"
        DownloadStatus.PAUSED -> Icons.Filled.Pause to if (task.error.isNotBlank()) "Paused — ${task.error}" else "Paused"
        DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle to "Downloaded — press OK to play"
        DownloadStatus.FAILED -> Icons.Filled.ErrorOutline to "Failed: ${task.error}"
    }
    ListItem(
        selected = false,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier.fillMaxWidth(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        colors = ListItemDefaults.colors(
            containerColor = SamColors.Surface,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        leadingContent = { Icon(icon, contentDescription = null, modifier = Modifier.size(30.dp)) },
        headlineContent = { Text(task.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Column {
                Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (task.status != DownloadStatus.COMPLETED) {
                    Spacer(Modifier.height(6.dp))
                    ProgressStrip(task.progress, Modifier.width(360.dp))
                }
            }
        },
        trailingContent = {
            val total = if (task.totalBytes > 0) " / ${formatBytes(task.totalBytes)}" else ""
            Text("${formatBytes(task.downloadedBytes)}$total", style = MaterialTheme.typography.labelMedium)
        },
    )
}

@Composable
private fun ConfirmDeleteDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cancelFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(520.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SamColors.Surface)
                .padding(28.dp),
        ) {
            Text("Delete download?", style = MaterialTheme.typography.headlineSmall, color = SamColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(
                "“$title” and its file will be removed from this device. This can't be undone.",
                style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary,
            )
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryActionButton("Cancel", Icons.Filled.Close, onDismiss, focusRequester = cancelFocus)
                SecondaryActionButton("Delete", Icons.Filled.Delete, onConfirm)
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { cancelFocus.requestFocus() } }
}
