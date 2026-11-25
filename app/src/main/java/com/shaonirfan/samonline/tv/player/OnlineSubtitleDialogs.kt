package com.shaonirfan.samonline.tv.player

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.TestLog
import com.shaonirfan.samonline.tv.data.remote.OnlineSubtitle
import com.shaonirfan.samonline.tv.data.remote.OpenSubtitlesException
import com.shaonirfan.samonline.tv.data.repository.OnlineSubtitleRepository
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import kotlinx.coroutines.launch

private sealed interface OnlineState {
    data object Searching : OnlineState
    data class Results(val subs: List<OnlineSubtitle>) : OnlineState
    data class Downloading(val sub: OnlineSubtitle) : OnlineState
    data class Failed(val message: String) : OnlineState
}

/**
 * "Search online (English)": fingerprint + title search on OpenSubtitles, pick one,
 * it's downloaded, cached for this video and attached at the current position.
 */
@Composable
fun OnlineSubtitleDialog(controller: PlayerController, repo: OnlineSubtitleRepository, onDone: () -> Unit) {
    val item = controller.current ?: return onDone()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<OnlineState>(OnlineState.Searching) }

    LaunchedEffect(item.url) {
        state = runCatching { repo.search(item.url, item.seriesTitle) }.fold(
            onSuccess = { subs ->
                TestLog.mark(
                    "online_search", "count" to subs.size, "hash_matches" to subs.count { it.hashMatch },
                    "top" to subs.firstOrNull()?.release,
                )
                if (subs.isEmpty()) OnlineState.Failed("No English subtitles found online for this video.") else OnlineState.Results(subs)
            },
            onFailure = { e ->
                TestLog.mark("online_search_error", "message" to e.message)
                OnlineState.Failed(e.message ?: "Couldn't reach OpenSubtitles. Is this TV connected to the internet?")
            },
        )
    }

    when (val s = state) {
        OnlineState.Searching -> StatusDialog("Searching OpenSubtitles…", "Matching this exact file first, then by title.", onDone)
        is OnlineState.Downloading -> StatusDialog("Downloading subtitle…", s.sub.release, onDone)
        is OnlineState.Failed -> StatusDialog("Online subtitles", s.message, onDone, showClose = true)
        is OnlineState.Results -> TrackDialog(
            title = "English subtitles online",
            options = s.subs.map { sub ->
                buildString {
                    if (sub.hashMatch) append("✓ Exact match · ")
                    append(sub.release.take(70))
                    if (sub.hearingImpaired) append(" · SDH")
                    append(" · ").append(formatCount(sub.downloads)).append(" downloads")
                } to false
            },
            onSelect = { i ->
                val sub = s.subs[i]
                state = OnlineState.Downloading(sub)
                scope.launch {
                    runCatching { repo.download(sub, item.url) }
                        .onSuccess { (_, remaining) ->
                            TestLog.mark("online_download", "ok" to true, "remaining" to remaining)
                            controller.attachOnlineSubtitle()
                            val left = if (remaining >= 0) " · $remaining downloads left today" else ""
                            Toast.makeText(context, "Subtitle added$left", Toast.LENGTH_LONG).show()
                            onDone()
                        }
                        .onFailure { e ->
                            TestLog.mark("online_download", "ok" to false, "message" to e.message)
                            val quota = (e as? OpenSubtitlesException)?.quotaExceeded == true
                            state = OnlineState.Failed(
                                if (quota) "${e.message}\n\nLog in under Settings → Subtitles account for 20 downloads a day."
                                else e.message ?: "Download failed",
                            )
                        }
                }
            },
            onDismiss = onDone,
        )
    }
}

/** Moves the downloaded subtitle earlier/later and re-attaches it. */
@Composable
fun SubtitleSyncDialog(controller: PlayerController, repo: OnlineSubtitleRepository, onDone: () -> Unit) {
    val item = controller.current ?: return onDone()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = listOf(-2000L, -1000L, -500L, 500L, 1000L, 2000L)
    TrackDialog(
        title = "Subtitle sync",
        options = steps.map { ms ->
            val sec = "%.1f s".format(kotlin.math.abs(ms) / 1000f)
            (if (ms < 0) "Show subtitles $sec earlier" else "Show subtitles $sec later") to false
        },
        onSelect = { i ->
            val file = repo.cached(item.url)
            if (file == null) {
                onDone()
            } else {
                scope.launch {
                    repo.shift(file, steps[i])
                    controller.attachOnlineSubtitle()
                    Toast.makeText(context, "Subtitles moved ${"%.1f".format(kotlin.math.abs(steps[i]) / 1000f)} s ${if (steps[i] < 0) "earlier" else "later"}", Toast.LENGTH_SHORT).show()
                    onDone()
                }
            }
        },
        onDismiss = onDone,
    )
}

@Composable
private fun StatusDialog(title: String, message: String, onDismiss: () -> Unit, showClose: Boolean = false) {
    val closeFocus = remember { FocusRequester() }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(480.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SamColors.Surface)
                .padding(28.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = SamColors.TextPrimary)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary)
            if (showClose) {
                Spacer(Modifier.height(20.dp))
                SecondaryActionButton(
                    "Close", Icons.Filled.Close, onDismiss,
                    modifier = Modifier.focusRequester(closeFocus),
                )
            }
        }
    }
    if (showClose) LaunchedEffect(Unit) { runCatching { closeFocus.requestFocus() } }
}

private fun formatCount(n: Int): String = when {
    n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
    n >= 1_000 -> "%.1fk".format(n / 1_000f)
    else -> n.toString()
}
