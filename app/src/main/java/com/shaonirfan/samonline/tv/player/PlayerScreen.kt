package com.shaonirfan.samonline.tv.player

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.PrimaryActionButton
import com.shaonirfan.samonline.tv.ui.components.SecondaryActionButton
import com.shaonirfan.samonline.tv.ui.components.formatDuration
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlayerDialog { NONE, SUBTITLES, AUDIO, SPEED }

private val speeds = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/**
 * Full-screen TV player. D-pad: OK = play/pause, ◀ / ▶ = seek 10 s (hold to scrub
 * faster), ▲ / ▼ = show controls. Media keys (play/pause, FF/RW, next/previous) work too.
 */
@Composable
fun PlayerScreen(onExit: () -> Unit) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val controller = remember { PlayerController(context, container, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    var controlsVisible by remember { mutableStateOf(true) }
    var lastInteraction by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var scrubTarget by remember { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf(PlayerDialog.NONE) }
    var upNextSeconds by remember { mutableIntStateOf(-1) }
    var rootFocused by remember { mutableStateOf(false) }
    var upNextCancelled by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val seekBarFocus = remember { FocusRequester() }
    val playButtonFocus = remember { FocusRequester() }

    fun touch() {
        lastInteraction = System.currentTimeMillis()
    }

    fun showControls(focusSeekBar: Boolean = true) {
        touch()
        controlsVisible = true
        if (focusSeekBar) {
            // Requested after the overlay is composed.
            scope.launchFocus(seekBarFocus)
        }
    }

    fun hideControls() {
        controlsVisible = false
        runCatching { rootFocus.requestFocus() }
    }

    // ---------------------------------------------------------------- lifecycle
    DisposableEffect(Unit) {
        controller.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    controller.player.pause()
                    controller.saveProgress()
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.release()
        }
    }

    // Auto-hide the overlay after 5 s of inactivity while playing.
    LaunchedEffect(controlsVisible, lastInteraction, controller.isPlaying, dialog) {
        if (controlsVisible && controller.isPlaying && dialog == PlayerDialog.NONE) {
            delay(5_000)
            hideControls()
        }
    }

    // Up-next countdown when an episode ends.
    LaunchedEffect(controller.ended) {
        upNextCancelled = false
        if (!controller.ended) {
            upNextSeconds = -1
            return@LaunchedEffect
        }
        if (controller.hasNext && container.settings.settings.value.autoplayNext) {
            for (s in 10 downTo 1) {
                if (upNextCancelled) return@LaunchedEffect
                upNextSeconds = s
                delay(1_000)
            }
            if (upNextCancelled) return@LaunchedEffect
            upNextSeconds = -1
            controller.next()
        } else if (!controller.hasNext) {
            delay(1_500)
            onExit()
        } else {
            showControls()
        }
    }

    BackHandler {
        when {
            dialog != PlayerDialog.NONE -> dialog = PlayerDialog.NONE
            upNextSeconds > 0 -> { upNextCancelled = true; upNextSeconds = -1; showControls() }
            controlsVisible && controller.isPlaying -> hideControls()
            else -> onExit()
        }
    }

    fun seekStep(repeat: Int): Long = when {
        repeat < 6 -> 10_000L
        repeat < 20 -> 30_000L
        else -> 60_000L
    }

    /** ◀ / ▶ handling shared by the hidden state and the focused seek bar: scrub while held, seek on release. */
    fun handleSeekKey(e: KeyEvent): Boolean {
        val forward = when (e.key) {
            Key.DirectionRight, Key.MediaFastForward -> true
            Key.DirectionLeft, Key.MediaRewind -> false
            else -> return false
        }
        when (e.type) {
            KeyEventType.KeyDown -> {
                touch()
                controlsVisible = true
                val base = scrubTarget ?: controller.positionMs
                val step = seekStep(e.nativeKeyEvent.repeatCount)
                val d = controller.durationMs
                val target = (base + if (forward) step else -step).let { if (d > 0) it.coerceIn(0, d) else it.coerceAtLeast(0) }
                scrubTarget = target
            }
            KeyEventType.KeyUp -> {
                scrubTarget?.let { controller.seekTo(it) }
                scrubTarget = null
            }
        }
        return true
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onFocusChanged { rootFocused = it.isFocused }
            .focusable()
            .onPreviewKeyEvent { e ->
                // Media keys work whatever has focus.
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.MediaPlayPause, Key.MediaPlay, Key.MediaPause -> { controller.togglePlay(); showControls(false); true }
                    Key.MediaNext -> { controller.next(); true }
                    Key.MediaPrevious -> { controller.previous(); true }
                    Key.MediaFastForward -> { controller.seekTo(controller.positionMs + 30_000); showControls(false); true }
                    Key.MediaRewind -> { controller.seekTo(controller.positionMs - 30_000); showControls(false); true }
                    Key.Captions -> { dialog = PlayerDialog.SUBTITLES; true }
                    else -> false
                }
            }
            .onKeyEvent { e ->
                // While an overlay button / the seek bar has focus, let normal focus movement happen.
                if (!rootFocused) {
                    touch()
                    return@onKeyEvent false
                }
                if (handleSeekKey(e)) return@onKeyEvent true
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        controller.togglePlay(); showControls(); true
                    }
                    Key.DirectionUp, Key.DirectionDown, Key.Menu -> { showControls(); true }
                    else -> false
                }
            },
    ) {
        // ------------------------------------------------------------ video surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    isFocusable = false
                    descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    setShutterBackgroundColor(AndroidColor.BLACK)
                    subtitleView?.apply {
                        setStyle(
                            CaptionStyleCompat(
                                AndroidColor.WHITE, AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT,
                                CaptionStyleCompat.EDGE_TYPE_OUTLINE, AndroidColor.BLACK, null,
                            )
                        )
                        setFractionalTextSize(0.058f)
                        setBottomPaddingFraction(0.08f)
                    }
                    player = controller.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (controller.isBuffering && controller.error == null) {
            BufferingSpinner(Modifier.align(Alignment.Center))
        }

        // ------------------------------------------------------------ overlay
        AnimatedVisibility(visible = controlsVisible || scrubTarget != null, enter = fadeIn(), exit = fadeOut()) {
            ControlsOverlay(
                controller = controller,
                scrubTarget = scrubTarget,
                seekBarFocus = seekBarFocus,
                playButtonFocus = playButtonFocus,
                onSeekKey = ::handleSeekKey,
                onInteract = ::touch,
                onDialog = { dialog = it; touch() },
            )
        }

        controller.resumedFromMs?.let { from ->
            LaunchedEffect(from) {
                delay(4_000)
                controller.resumedFromMs = null
            }
            Text(
                "Resumed from ${formatDuration(from)}",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.SafeHorizontal, Dimens.SafeVertical)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (upNextSeconds > 0) {
            UpNextCard(
                title = controller.queue.getOrNull(controller.index + 1)?.title.orEmpty(),
                seconds = upNextSeconds,
                onPlayNow = { upNextSeconds = -1; controller.next() },
                onCancel = { upNextCancelled = true; upNextSeconds = -1; showControls() },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        controller.error?.let { message ->
            ErrorOverlay(message, onRetry = { controller.retry() }, onBack = onExit)
        }

        when (dialog) {
            PlayerDialog.SUBTITLES -> TrackDialog(
                title = "Subtitles",
                options = remember(dialog) { controller.textOptions() }.map { it.label to it.selected },
                onSelect = { i -> controller.selectText(controller.textOptions()[i]); dialog = PlayerDialog.NONE },
                onDismiss = { dialog = PlayerDialog.NONE },
            )
            PlayerDialog.AUDIO -> TrackDialog(
                title = "Audio",
                options = remember(dialog) { controller.audioOptions() }.map { it.label to it.selected },
                onSelect = { i -> controller.selectAudio(controller.audioOptions()[i]); dialog = PlayerDialog.NONE },
                onDismiss = { dialog = PlayerDialog.NONE },
            )
            PlayerDialog.SPEED -> TrackDialog(
                title = "Playback speed",
                options = speeds.map { (if (it == 1f) "Normal" else "${it}x") to (it == controller.speed) },
                onSelect = { i -> controller.setPlaybackSpeed(speeds[i]); dialog = PlayerDialog.NONE },
                onDismiss = { dialog = PlayerDialog.NONE },
            )
            PlayerDialog.NONE -> Unit
        }
    }

    LaunchedEffect(Unit) {
        // Start with the overlay visible and the play/pause button focused.
        delay(100)
        runCatching { playButtonFocus.requestFocus() }
    }
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) runCatching { rootFocus.requestFocus() }
    }
}

private fun CoroutineScope.launchFocus(requester: FocusRequester) {
    launch {
        repeat(5) {
            delay(32)
            if (runCatching { requester.requestFocus() }.isSuccess) return@launch
        }
    }
}

@Composable
private fun ControlsOverlay(
    controller: PlayerController,
    scrubTarget: Long?,
    seekBarFocus: FocusRequester,
    playButtonFocus: FocusRequester,
    onSeekKey: (KeyEvent) -> Boolean,
    onInteract: () -> Unit,
    onDialog: (PlayerDialog) -> Unit,
) {
    val item = controller.current
    Box(Modifier.fillMaxSize()) {
        // Top and bottom scrims.
        Box(
            Modifier.fillMaxWidth().height(160.dp)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
        )
        Box(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )

        Column(Modifier.padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical)) {
            if (item?.seriesTitle?.isNotBlank() == true && item.seriesTitle != item.title) {
                Text(item.seriesTitle, style = MaterialTheme.typography.titleMedium, color = SamColors.TextSecondary)
            }
            Text(
                item?.title.orEmpty(), style = MaterialTheme.typography.headlineSmall, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = Dimens.SafeHorizontal, vertical = Dimens.SafeVertical),
        ) {
            SeekBar(
                position = scrubTarget ?: controller.positionMs,
                buffered = controller.bufferedMs,
                duration = controller.durationMs,
                scrubbing = scrubTarget != null,
                focusRequester = seekBarFocus,
                onKey = onSeekKey,
                onCenter = { controller.togglePlay(); onInteract() },
            )
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (controller.hasPrevious) {
                    ControlButton(Icons.Filled.SkipPrevious, "Previous") { controller.previous(); onInteract() }
                }
                ControlButton(Icons.Filled.Replay10, "Back 10 seconds") { controller.seekTo(controller.positionMs - 10_000); onInteract() }
                ControlButton(
                    if (controller.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (controller.isPlaying) "Pause" else "Play",
                    large = true,
                    modifier = Modifier.focusRequester(playButtonFocus),
                ) { controller.togglePlay(); onInteract() }
                ControlButton(Icons.Filled.Forward10, "Forward 10 seconds") { controller.seekTo(controller.positionMs + 10_000); onInteract() }
                if (controller.hasNext) {
                    ControlButton(Icons.Filled.SkipNext, "Next episode", label = "Next Episode") { controller.next(); onInteract() }
                }
                Spacer(Modifier.weight(1f))
                ControlButton(Icons.Filled.ClosedCaption, "Subtitles", label = "Subtitles") { onDialog(PlayerDialog.SUBTITLES) }
                ControlButton(Icons.Filled.Audiotrack, "Audio", label = "Audio") { onDialog(PlayerDialog.AUDIO) }
                ControlButton(
                    Icons.Filled.Speed, "Speed",
                    label = if (controller.speed == 1f) "Speed" else "${controller.speed}x",
                ) { onDialog(PlayerDialog.SPEED) }
            }
        }
    }
}

@Composable
private fun SeekBar(
    position: Long,
    buffered: Long,
    duration: Long,
    scrubbing: Boolean,
    focusRequester: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    onCenter: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                formatDuration(position),
                style = MaterialTheme.typography.titleMedium,
                color = if (scrubbing) SamColors.AccentSoft else Color.White,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "-" + formatDuration((duration - position).coerceAtLeast(0)),
                style = MaterialTheme.typography.titleMedium, color = SamColors.TextSecondary,
            )
        }
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(
            Modifier
                .fillMaxWidth()
                .height(24.dp)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused }
                .onKeyEvent { e ->
                    if (onKey(e)) return@onKeyEvent true
                    if (e.type == KeyEventType.KeyDown &&
                        (e.key == Key.DirectionCenter || e.key == Key.Enter || e.key.nativeKeyCode == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER)
                    ) {
                        onCenter(); true
                    } else false
                }
                .focusable(),
            contentAlignment = Alignment.CenterStart,
        ) {
            val barHeight = if (focused) 8.dp else 5.dp
            Box(Modifier.fillMaxWidth().height(barHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.25f)))
            Box(Modifier.fillMaxWidth(bufferedFraction).height(barHeight).clip(RoundedCornerShape(4.dp)).background(Color.White.copy(alpha = 0.45f)))
            Box(Modifier.fillMaxWidth(fraction).height(barHeight).clip(RoundedCornerShape(4.dp)).background(SamColors.Accent))
            val thumb = if (focused) 22.dp else 14.dp
            Box(
                Modifier
                    .offset(x = (maxWidth - thumb) * fraction)
                    .size(thumb)
                    .clip(CircleShape)
                    .background(if (focused) Color.White else SamColors.Accent)
                    .then(if (focused) Modifier.border(3.dp, SamColors.Accent, CircleShape) else Modifier)
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    description: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    large: Boolean = false,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(if (large) 60.dp else 50.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(30.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Dimens.FocusScale),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.12f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(
            Modifier.padding(horizontal = if (label != null) 18.dp else if (large) 16.dp else 13.dp).height(if (large) 60.dp else 50.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(if (large) 30.dp else 24.dp))
            if (label != null) {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun BufferingSpinner(modifier: Modifier) {
    androidx.compose.foundation.layout.Box(modifier.size(72.dp), contentAlignment = Alignment.Center) {
        com.shaonirfan.samonline.tv.ui.components.LoadingState(message = "")
    }
}

@Composable
private fun UpNextCard(title: String, seconds: Int, onPlayNow: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    val playFocus = remember { FocusRequester() }
    Column(
        modifier
            .padding(Dimens.SafeHorizontal, Dimens.SafeVertical + 20.dp)
            .width(420.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SamColors.Surface.copy(alpha = 0.95f))
            .padding(22.dp),
    ) {
        Text("Next episode in $seconds", style = MaterialTheme.typography.titleMedium, color = SamColors.TextSecondary)
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PrimaryActionButton("Play now", Icons.Filled.PlayArrow, onPlayNow, focusRequester = playFocus)
            SecondaryActionButton("Cancel", Icons.Filled.Pause, onCancel)
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit, onBack: () -> Unit) {
    val retryFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Playback problem", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Text(message, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary)
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                PrimaryActionButton("Try again", Icons.Filled.Replay10, onRetry, focusRequester = retryFocus)
                SecondaryActionButton("Back", Icons.Filled.SkipPrevious, onBack)
            }
        }
    }
    LaunchedEffect(message) { runCatching { retryFocus.requestFocus() } }
}
