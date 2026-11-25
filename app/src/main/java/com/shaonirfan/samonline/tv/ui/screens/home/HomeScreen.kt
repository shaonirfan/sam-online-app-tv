package com.shaonirfan.samonline.tv.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.Carousel
import androidx.tv.material3.CarouselDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.tv.material3.rememberCarouselState
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.ui.components.Backdrop
import com.shaonirfan.samonline.tv.ui.components.ContentRow
import com.shaonirfan.samonline.tv.ui.components.ErrorState
import com.shaonirfan.samonline.tv.ui.components.FocusMemory
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.PivotScrolling
import com.shaonirfan.samonline.tv.ui.components.PosterImage
import com.shaonirfan.samonline.tv.ui.components.PrimaryActionButton
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import com.shaonirfan.samonline.tv.ui.components.SectionTitle
import com.shaonirfan.samonline.tv.ui.components.SkeletonRow
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.components.rememberPoster
import com.shaonirfan.samonline.tv.ui.navigation.openItem
import com.shaonirfan.samonline.tv.ui.navigation.play
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: HomeViewModel = viewModel { HomeViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val continueWatching by vm.continueWatching.collectAsStateWithLifecycle()
    val myList by vm.myList.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val focus = rememberFocusMemory("home")
    val listState = rememberLazyListState()
    var backdrop by remember { mutableStateOf<String?>(null) }

    fun playItem(item: MediaItem) {
        scope.launch {
            vm.resolvePlay(item)
                .onSuccess { nav.play(it.queue, it.index, it.positionMs) }
                .onFailure { nav.openItem(item) }
        }
    }

    // Back from deep in the rows first returns to the top, like Netflix.
    BackHandler(enabled = listState.firstVisibleItemIndex > 0) {
        scope.launch {
            listState.animateScrollToItem(0)
            runCatching { focus.initialRequester.requestFocus() }
        }
    }

    val hasContent = state.hero.isNotEmpty() || state.rows.any { it.items.isNotEmpty() }
    if (state.allFailed && continueWatching.isEmpty()) {
        ErrorState("Couldn't reach the SAM Online servers.", onRetry = { vm.refresh() })
        return
    }

    Box(Modifier.fillMaxSize()) {
        Backdrop(backdrop)
        PivotScrolling {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "hero") {
                    if (state.hero.isNotEmpty()) {
                        HeroCarousel(
                            items = state.hero,
                            focus = focus,
                            autoRotate = vm.heroAutoRotate,
                            onPlay = ::playItem,
                            onInfo = { nav.openItem(it) },
                            onFocusedSlide = { backdrop = null },
                        )
                    } else {
                        Box(Modifier.fillMaxWidth().height(80.dp))
                    }
                }

                if (continueWatching.isNotEmpty()) {
                    item(key = "continue") {
                        val items = continueWatching.map {
                            MediaItem(
                                title = listOf(it.seriesTitle, it.title).filter { s -> s.isNotBlank() }.distinct().joinToString(" · "),
                                url = it.url, poster = it.poster, isVideo = true,
                            )
                        }
                        ContentRow(
                            title = "Continue Watching",
                            rowKey = "continue",
                            items = items,
                            focusMemory = focus,
                            progressFor = { item -> continueWatching.firstOrNull { it.url == item.url }?.fraction },
                            onFocused = { backdrop = it.poster },
                            onClick = { item ->
                                val p = continueWatching.first { it.url == item.url }
                                scope.launch {
                                    val plan = vm.resume(p)
                                    nav.play(plan.queue, plan.index, plan.positionMs)
                                }
                            },
                            firstItemModifier = if (state.hero.isEmpty()) Modifier.initialFocus(focus) else Modifier,
                        )
                    }
                }

                if (myList.isNotEmpty()) {
                    item(key = "mylist") {
                        ContentRow(
                            title = "My List", rowKey = "mylist", items = myList, focusMemory = focus,
                            onFocused = { backdrop = it.poster }, onClick = { nav.openItem(it) },
                        )
                    }
                }

                items(state.rows, key = { it.category.id }) { row ->
                    when {
                        row.items.isNotEmpty() -> ContentRow(
                            title = row.category.name,
                            rowKey = row.category.id,
                            items = row.items,
                            focusMemory = focus,
                            onFocused = { backdrop = it.poster },
                            onClick = { nav.openItem(it) },
                            firstItemModifier = if (state.hero.isEmpty() && continueWatching.isEmpty() &&
                                row == state.rows.firstOrNull { it.items.isNotEmpty() }
                            ) Modifier.initialFocus(focus) else Modifier,
                        )
                        row.loading -> Column {
                            SectionTitle(row.category.name)
                            SkeletonRow()
                        }
                        else -> Unit // failed with nothing cached: hide the row
                    }
                }
            }
        }
    }
    FocusRestoreEffect(focus, ready = hasContent || continueWatching.isNotEmpty())
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun HeroCarousel(
    items: List<MediaItem>,
    focus: FocusMemory,
    autoRotate: Boolean,
    onPlay: (MediaItem) -> Unit,
    onInfo: (MediaItem) -> Unit,
    onFocusedSlide: () -> Unit,
) {
    val carouselState = rememberCarouselState()
    Carousel(
        itemCount = items.size,
        carouselState = carouselState,
        autoScrollDurationMillis = if (autoRotate) 7_000L else Long.MAX_VALUE,
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .onFocusChanged { if (it.hasFocus) onFocusedSlide() },
        carouselIndicator = {
            CarouselDefaults.IndicatorRow(
                itemCount = items.size,
                activeItemIndex = carouselState.activeItemIndex,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = Dimens.SafeHorizontal, bottom = 20.dp),
            )
        },
    ) { index ->
        val item = items[index]
        val poster = rememberPoster(item.url, item.poster)
        Box(Modifier.fillMaxSize()) {
            // Blurred, darkened poster as the slide background.
            Backdrop(poster, alpha = 0.6f)
            // Sharp poster on the right.
            PosterImage(
                poster, item.displayTitle,
                Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = Dimens.SafeHorizontal + 40.dp, top = 30.dp, bottom = 50.dp)
                    .fillMaxHeight()
                    .width(220.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(0.35f to SamColors.Background.copy(alpha = 0.85f), 0.7f to Color.Transparent)
                )
            )
            Column(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = Dimens.SafeHorizontal, top = Dimens.SafeVertical)
                    .width(520.dp),
            ) {
                Text(
                    item.subtitle.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = SamColors.AccentSoft,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    item.displayTitle,
                    style = MaterialTheme.typography.displaySmall,
                    color = SamColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                item.year?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.titleMedium, color = SamColors.TextSecondary)
                }
                Spacer(Modifier.height(24.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    PrimaryActionButton(
                        "Play", Icons.Filled.PlayArrow, { onPlay(item) },
                        modifier = Modifier.initialFocus(focus).rememberFocus(focus, "hero-play"),
                    )
                    SecondaryActionButton("More Info", Icons.Filled.Info, { onInfo(item) })
                }
            }
        }
    }
}
