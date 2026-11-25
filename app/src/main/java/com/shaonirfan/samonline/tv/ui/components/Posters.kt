package com.shaonirfan.samonline.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.shaonirfan.samonline.tv.AppContainer
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Poster URL for an item: known immediately if cached, otherwise discovered in the background. */
@Composable
fun rememberPoster(url: String, initial: String?): String? {
    val container = LocalAppContainer.current
    var poster by remember(url) { mutableStateOf(initial ?: container.catalog.cachedPoster(url)) }
    LaunchedEffect(url) {
        if (poster == null) poster = container.catalog.posterFor(url)
    }
    return poster
}

@Composable
fun PosterImage(
    url: String?,
    title: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val context = LocalContext.current
    if (url == null) {
        PosterPlaceholder(title, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = remember(url) { ImageRequest.Builder(context).data(url).crossfade(true).build() },
        contentDescription = title,
        contentScale = contentScale,
        modifier = modifier,
        loading = { PosterPlaceholder(title, Modifier.fillMaxSize(), showTitle = false) },
        error = { PosterPlaceholder(title, Modifier.fillMaxSize()) },
    )
}

@Composable
fun PosterPlaceholder(title: String, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    Box(
        modifier.background(
            Brush.linearGradient(listOf(Color(0xFF2A1A22), Color(0xFF1A1A26), Color(0xFF101018)))
        ),
        contentAlignment = Alignment.Center,
    ) {
        if (showTitle) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = SamColors.TextSecondary,
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/**
 * The focusable 2:3 poster used in every row and grid: scales to 1.08x, gets a white
 * border and a red glow when focused, and can show a watch-progress bar.
 */
@Composable
fun PosterCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = Dimens.PosterWidth,
    progress: Float? = null,
    showTitle: Boolean = true,
    caption: String? = null,
    onFocused: (MediaItem) -> Unit = {},
) {
    val poster = rememberPoster(item.url, item.poster)
    var focused by remember { mutableStateOf(false) }
    Column(modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(width * 1.5f)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused(item.copy(poster = poster))
                },
            shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
            scale = CardDefaults.scale(focusedScale = Dimens.FocusScale),
            border = CardDefaults.border(
                focusedBorder = Border(BorderStroke(3.dp, SamColors.FocusRing), shape = RoundedCornerShape(10.dp)),
            ),
            glow = CardDefaults.glow(
                focusedGlow = Glow(elevationColor = SamColors.Accent.copy(alpha = 0.55f), elevation = 18.dp),
            ),
            colors = CardDefaults.colors(containerColor = SamColors.Surface),
        ) {
            Box(Modifier.fillMaxSize()) {
                PosterImage(poster, item.displayTitle, Modifier.fillMaxSize())
                if (progress != null && progress > 0f) {
                    ProgressStrip(
                        progress,
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }
            }
        }
        if (showTitle) {
            Text(
                text = item.displayTitle,
                style = MaterialTheme.typography.labelMedium,
                color = if (focused) SamColors.TextPrimary else SamColors.TextMuted,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 10.dp, start = 2.dp, end = 2.dp),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = SamColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

@Composable
fun ProgressStrip(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(SamColors.ProgressTrack)
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(SamColors.Progress)
        )
    }
}

@Composable
fun BadgeDot(modifier: Modifier = Modifier) {
    Box(modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(SamColors.Accent))
}
