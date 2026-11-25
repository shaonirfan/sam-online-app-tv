package com.shaonirfan.samonline.tv.ui.components

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors

/** Full-screen, dimmed (and blurred on Android 12+) backdrop built from a poster. */
@Composable
fun Backdrop(imageUrl: String?, modifier: Modifier = Modifier, alpha: Float = 0.45f) {
    Box(modifier.fillMaxSize().background(SamColors.Background)) {
        Crossfade(targetState = imageUrl, animationSpec = tween(600), label = "backdrop") { url ->
            if (url != null) {
                val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(24.dp) else Modifier
                PosterImage(url, "", Modifier.fillMaxSize().then(blurred).alpha(alpha))
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to SamColors.Background,
                        0.45f to SamColors.Background.copy(alpha = 0.75f),
                        1f to Color.Transparent,
                    )
                )
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to SamColors.Background))
        )
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = SamColors.TextPrimary,
        modifier = modifier.padding(start = Dimens.SafeHorizontal, bottom = 14.dp),
    )
}

@Composable
fun PrimaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    Button(
        onClick = onClick,
        modifier = if (focusRequester != null) modifier.focusRequester(focusRequester) else modifier,
        scale = ButtonDefaults.scale(focusedScale = Dimens.FocusScale),
        glow = ButtonDefaults.glow(focusedGlow = androidx.tv.material3.Glow(SamColors.Accent.copy(alpha = 0.6f), 14.dp)),
        colors = ButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.92f),
            contentColor = Color.Black,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryActionButton(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        scale = OutlinedButtonDefaults.scale(focusedScale = Dimens.FocusScale),
        colors = OutlinedButtonDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = SamColors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
        contentPadding = OutlinedButtonDefaults.ButtonWithIconContentPadding,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun LoadingState(message: String = "Loading…", modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "loading")
    val a by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse",
    )
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) { i ->
                Box(
                    Modifier
                        .size(14.dp)
                        .alpha(if (i == 1) a else 1.3f - a)
                        .clip(RoundedCornerShape(7.dp))
                        .background(SamColors.Accent)
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary)
    }
}

/** Shimmering placeholder row shown while a category row is crawled. */
@Composable
fun SkeletonRow(count: Int = 7) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val a by transition.animateFloat(
        initialValue = 0.25f, targetValue = 0.55f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shimmer",
    )
    Row(
        Modifier.fillMaxWidth().padding(start = Dimens.SafeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(Dimens.RowSpacing),
    ) {
        repeat(count) {
            Box(
                Modifier
                    .width(Dimens.PosterWidth)
                    .height(Dimens.PosterHeight)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SamColors.SurfaceRaised.copy(alpha = a))
            )
        }
    }
}

@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    retryFocus: FocusRequester? = null,
) {
    Column(modifier.fillMaxSize().padding(Dimens.SafeHorizontal), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = SamColors.AccentSoft, modifier = Modifier.size(56.dp))
        Spacer(Modifier.height(16.dp))
        Text(message, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "Make sure this device is on a SAM Online / BDIX connection.",
            style = MaterialTheme.typography.bodyMedium, color = SamColors.TextMuted, textAlign = TextAlign.Center,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(24.dp))
            PrimaryActionButton(
                "Try again", Icons.Filled.Refresh, onRetry,
                focusRequester = retryFocus,
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024.0) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
