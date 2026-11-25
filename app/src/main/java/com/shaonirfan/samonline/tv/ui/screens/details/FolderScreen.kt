package com.shaonirfan.samonline.tv.ui.screens.details

import android.widget.Toast
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.data.download.EnqueueResult
import com.shaonirfan.samonline.tv.data.model.Episode
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.model.WatchProgress
import com.shaonirfan.samonline.tv.ui.components.Backdrop
import com.shaonirfan.samonline.tv.ui.components.ContentRow
import com.shaonirfan.samonline.tv.ui.components.ErrorState
import com.shaonirfan.samonline.tv.ui.components.FocusMemory
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LoadingState
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.MinimalScrolling
import com.shaonirfan.samonline.tv.ui.components.PivotScrolling
import com.shaonirfan.samonline.tv.ui.components.PosterCard
import com.shaonirfan.samonline.tv.ui.components.PosterImage
import com.shaonirfan.samonline.tv.ui.components.PrimaryActionButton
import com.shaonirfan.samonline.tv.ui.components.ProgressStrip
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import com.shaonirfan.samonline.tv.ui.components.formatDuration
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.navigation.openItem
import com.shaonirfan.samonline.tv.ui.navigation.play
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import kotlinx.coroutines.launch

@Composable
fun FolderScreen(nav: NavController, url: String, title: String, posterHint: String?) {
    val container = LocalAppContainer.current
    val vm: FolderViewModel = viewModel(key = url) { FolderViewModel(container, url, title, posterHint) }
    val state by vm.state.collectAsStateWithLifecycle()
    val focus = rememberFocusMemory("folder:$url")

    when (val s = state) {
        FolderUiState.Loading -> Box(Modifier.fillMaxSize()) {
            Backdrop(posterHint)
            LoadingState("Opening ${NameCleaner.displayTitle(title)}…")
        }
        is FolderUiState.Error -> ErrorState(s.message, onRetry = vm::load, retryFocus = focus.initialRequester)
        is FolderUiState.Grid -> BrowseGrid(NameCleaner.decode(title), s.items, focus, onClick = { nav.openItem(it) })
        is FolderUiState.Details -> DetailsContent(nav, vm, s, focus)
    }
    FocusRestoreEffect(focus, ready = state !is FolderUiState.Loading)
}

/** Category / sub-folder grid: 5 posters per row inside the overscan-safe area. */
@Composable
fun BrowseGrid(
    title: String,
    items: List<MediaItem>,
    focus: FocusMemory,
    onClick: (MediaItem) -> Unit,
    columns: Int = 5,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(start = Dimens.SafeHorizontal, end = Dimens.SafeHorizontal, top = Dimens.SafeVertical, bottom = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = SamColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("${items.size} titles", style = MaterialTheme.typography.bodyMedium, color = SamColors.TextMuted)
        }
        if (items.isEmpty()) {
            ErrorState("This folder is empty.", onRetry = null)
            return
        }
        PivotScrolling(0.08f) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = rememberLazyGridState(),
                contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(26.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.RowSpacing),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items, key = { _, it -> it.url }) { index, item ->
                    Box(contentAlignment = Alignment.TopCenter) {
                        PosterCard(
                            item = item,
                            onClick = { onClick(item) },
                            modifier = Modifier
                                .rememberFocus(focus, item.url)
                                .then(if (index == 0) Modifier.initialFocus(focus) else Modifier),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DetailsContent(nav: NavController, vm: FolderViewModel, s: FolderUiState.Details, focus: FocusMemory) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val saved by vm.isSaved.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val item = s.content.item
    val isSeries = s.content.isSeries
    val last = remember(progress) { vm.lastWatched() }

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()

    fun download(ep: Episode) {
        if (!vm.downloadsEnabled) return toast("Downloads are turned off in Settings")
        when (val r = vm.download(ep)) {
            is EnqueueResult.Added -> toast("Added to downloads: ${r.fileName}")
            EnqueueResult.AlreadyDone -> toast("Already downloaded")
            EnqueueResult.AlreadyQueued -> toast("Already in the download queue")
            EnqueueResult.Resumed -> toast("Download resumed")
        }
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(item.poster, alpha = 0.5f)
        PivotScrolling(0.1f) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // ---------------------------------------------------------- header
                item(key = "header") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(start = Dimens.SafeHorizontal, end = Dimens.SafeHorizontal, top = Dimens.SafeVertical),
                    ) {
                        Column(Modifier.weight(1f).padding(end = 32.dp), verticalArrangement = Arrangement.Center) {
                            Text(item.subtitle.uppercase(), style = MaterialTheme.typography.labelLarge, color = SamColors.AccentSoft)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                item.displayTitle, style = MaterialTheme.typography.displaySmall, color = SamColors.TextPrimary,
                                maxLines = 2, overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(10.dp))
                            val meta = buildList {
                                item.year?.let { add(it) }
                                if (isSeries) {
                                    add(if (s.content.seasons.isNotEmpty()) "${s.content.seasons.size} Seasons" else "${s.content.files.size} Episodes")
                                } else {
                                    add("Movie")
                                    s.content.files.firstOrNull()?.sizeLabel?.takeIf { it.isNotBlank() }?.let { add(it) }
                                    NameCleaner.decode(s.content.files.firstOrNull()?.url?.substringAfterLast('/').orEmpty())
                                        .let { Regex("(?i)(2160p|1080p|720p|480p)").find(it)?.value }?.let { add(it) }
                                }
                            }
                            Text(meta.joinToString("  •  "), style = MaterialTheme.typography.titleMedium, color = SamColors.TextSecondary)
                            if (last != null && last.isInProgress) {
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    "${if (isSeries) last.title + " · " else ""}${formatDuration(last.durationMs - last.positionMs)} left",
                                    style = MaterialTheme.typography.bodyMedium, color = SamColors.TextSecondary,
                                )
                                Spacer(Modifier.height(6.dp))
                                ProgressStrip(last.fraction, Modifier.width(260.dp))
                            }
                            Spacer(Modifier.height(26.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                val resume = last != null && last.isInProgress
                                PrimaryActionButton(
                                    text = if (resume) "Resume" else "Play",
                                    icon = if (resume) Icons.Filled.Replay else Icons.Filled.PlayArrow,
                                    onClick = {
                                        scope.launch {
                                            vm.playPlan()
                                                .onSuccess { nav.play(it.queue, it.index, it.positionMs) }
                                                .onFailure { e -> toast(e.localizedMessage ?: "Nothing to play") }
                                        }
                                    },
                                    modifier = Modifier.initialFocus(focus).rememberFocus(focus, "play"),
                                )
                                SecondaryActionButton(
                                    text = "My List",
                                    icon = if (saved) Icons.Filled.Check else Icons.Filled.Add,
                                    onClick = { toast(if (vm.toggleMyList()) "Added to My List" else "Removed from My List") },
                                    modifier = Modifier.rememberFocus(focus, "mylist"),
                                )
                                if (!isSeries && s.content.files.isNotEmpty()) {
                                    SecondaryActionButton(
                                        text = "Download",
                                        icon = Icons.Filled.Download,
                                        onClick = { download(s.content.files.first()) },
                                        modifier = Modifier.rememberFocus(focus, "download"),
                                    )
                                }
                            }
                            if (isSeries) {
                                Spacer(Modifier.height(14.dp))
                                Text("Tip: hold OK on an episode to download it", style = MaterialTheme.typography.bodySmall, color = SamColors.TextMuted)
                            }
                        }
                        PosterImage(
                            item.poster, item.displayTitle,
                            Modifier.width(230.dp).height(345.dp).align(Alignment.CenterVertically).clip(RoundedCornerShape(12.dp)),
                        )
                    }
                }

                // ---------------------------------------------------------- seasons
                if (s.content.seasons.isNotEmpty()) {
                    item(key = "seasons") {
                        MinimalScrolling {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.focusRestorer(),
                            ) {
                                itemsIndexed(s.content.seasons, key = { _, it -> it.url }) { index, season ->
                                    FilterChip(
                                        selected = index == s.seasonIndex,
                                        onClick = { vm.selectSeason(index) },
                                        modifier = Modifier.rememberFocus(focus, "season|${season.url}"),
                                        scale = FilterChipDefaults.scale(focusedScale = 1.06f),
                                        colors = FilterChipDefaults.colors(
                                            selectedContainerColor = SamColors.Accent,
                                            selectedContentColor = androidx.compose.ui.graphics.Color.White,
                                            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                                            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                                            focusedSelectedContainerColor = androidx.compose.ui.graphics.Color.White,
                                            focusedSelectedContentColor = androidx.compose.ui.graphics.Color.Black,
                                        ),
                                    ) { Text(NameCleaner.displayTitle(season.name).ifBlank { season.name }, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                    }
                }

                // ---------------------------------------------------------- episodes / files
                if (s.episodesLoading) {
                    item(key = "eps-loading") {
                        Text("Loading episodes…", color = SamColors.TextSecondary, modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 12.dp))
                    }
                } else if (isSeries || s.episodes.size > 1) {
                    item(key = "eps-title") {
                        Text(
                            if (isSeries) "Episodes" else "Files",
                            style = MaterialTheme.typography.titleLarge, color = SamColors.TextPrimary,
                            modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 8.dp),
                        )
                    }
                    itemsIndexed(s.episodes, key = { _, it -> "ep|" + it.url }) { index, ep ->
                        EpisodeRow(
                            episode = ep,
                            progress = progress[ep.url],
                            isLastWatched = last?.url == ep.url,
                            onPlay = { vm.planForEpisode(index)?.let { nav.play(it.queue, it.index, it.positionMs) } },
                            onLongPress = { download(ep) },
                            modifier = Modifier.rememberFocus(focus, "ep|${ep.url}"),
                        )
                    }
                }

                // ---------------------------------------------------------- related
                if (s.related.isNotEmpty()) {
                    item(key = "related") {
                        Spacer(Modifier.height(18.dp))
                        ContentRow(
                            title = "More Like This", rowKey = "related", items = s.related, focusMemory = focus,
                            onClick = { nav.openItem(it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    progress: WatchProgress?,
    isLastWatched: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        selected = isLastWatched,
        onClick = onPlay,
        onLongClick = onLongPress,
        modifier = modifier.padding(horizontal = Dimens.SafeHorizontal).fillMaxWidth(),
        scale = ListItemDefaults.scale(focusedScale = 1.02f),
        colors = ListItemDefaults.colors(
            containerColor = SamColors.Surface.copy(alpha = 0.7f),
            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
            selectedContainerColor = SamColors.SurfaceRaised,
        ),
        leadingContent = {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
        },
        headlineContent = {
            Text(episode.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Column {
                Text(
                    NameCleaner.decode(episode.url.substringAfterLast('/')),
                    style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (progress != null && progress.fraction > 0.01f) {
                    Spacer(Modifier.height(6.dp))
                    ProgressStrip(progress.fraction, Modifier.width(220.dp))
                }
            }
        },
        trailingContent = {
            if (episode.sizeLabel.isNotBlank()) Text(episode.sizeLabel, style = MaterialTheme.typography.labelMedium)
        },
    )
}
