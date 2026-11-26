package com.shaonirfan.samonline.tv.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.shaonirfan.samonline.tv.AppContainer
import com.shaonirfan.samonline.tv.TestLog
import com.shaonirfan.samonline.tv.data.model.WatchProgress
import com.shaonirfan.samonline.tv.data.remote.SubtitleFinder
import com.shaonirfan.samonline.tv.data.remote.SubtitleSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale

data class TrackOption(
    val label: String,
    val selected: Boolean,
    /** null = "Off" */
    val group: Tracks.Group?,
    val index: Int,
)

/**
 * Owns the ExoPlayer instance and all playback logic so the composable only renders
 * state: queue / next episode, side-loaded subtitles, track + speed selection,
 * resume positions and periodic progress saving.
 */
@UnstableApi
class PlayerController(
    context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope,
) {
    private val trackSelector = DefaultTrackSelector(context)

    val player: ExoPlayer = ExoPlayer.Builder(
        context,
        // Platform decoders first, FFmpeg as fallback for AC3 / EAC3 / DTS / TrueHD audio in MKVs.
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true),
    )
        .setTrackSelector(trackSelector)
        .setSeekBackIncrementMs(10_000)
        .setSeekForwardIncrementMs(10_000)
        .build()

    var queue by mutableStateOf(PlaybackQueue.items)
        private set
    var index by mutableIntStateOf(PlaybackQueue.startIndex)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(true)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var bufferedMs by mutableLongStateOf(0L)
        private set
    var ended by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var speed by mutableFloatStateOf(container.settings.playbackSpeed)
        private set
    var hasSubtitles by mutableStateOf(false)
        private set
    var hasMultipleAudio by mutableStateOf(false)
        private set
    var resumedFromMs by mutableStateOf<Long?>(null)

    val current: PlayItem? get() = queue.getOrNull(index)
    val hasNext: Boolean get() = index < queue.lastIndex
    val hasPrevious: Boolean get() = index > 0

    private var loadJob: Job? = null
    private var tickerJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying = playing
            if (!playing) saveProgress()
        }

        override fun onPlaybackStateChanged(state: Int) {
            isBuffering = state == Player.STATE_BUFFERING
            if (state == Player.STATE_READY) {
                durationMs = player.duration.coerceAtLeast(0L)
                TestLog.mark("player_ready", "title" to current?.title, "duration_s" to durationMs / 1000, "pos_s" to player.currentPosition / 1000)
            }
            if (state == Player.STATE_ENDED) {
                ended = true
                saveProgress(finished = true)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            hasSubtitles = tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            hasMultipleAudio = tracks.groups.count { it.type == C.TRACK_TYPE_AUDIO } > 1 ||
                tracks.groups.any { it.type == C.TRACK_TYPE_AUDIO && it.length > 1 }
        }

        override fun onPlayerError(e: PlaybackException) {
            TestLog.mark("player_error", "code" to e.errorCodeName)
            error = when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Can't reach the server. Check your SAM Online connection."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
                PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "This file is no longer available on the server."
                PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "This device can't decode this video format."
                else -> e.localizedMessage ?: "Playback error"
            }
        }
    }

    init {
        player.addListener(listener)
        player.setPlaybackSpeed(speed)
        val subsOn = container.settings.settings.value.subtitlesByDefault
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subsOn)
            .setPreferredTextLanguages("en", "und")
            .setSelectUndeterminedTextLanguage(true)
            .build()
        tickerJob = scope.launch {
            var sinceSave = 0
            while (isActive) {
                positionMs = player.currentPosition.coerceAtLeast(0L)
                bufferedMs = player.bufferedPosition.coerceAtLeast(0L)
                if (player.duration > 0) durationMs = player.duration
                if (++sinceSave >= 10) { // every ~5 s
                    sinceSave = 0
                    TestLog.mark("player_tick", "pos_s" to positionMs / 1000, "playing" to player.isPlaying, "buffered_s" to bufferedMs / 1000)
                    if (player.isPlaying) saveProgress()
                }
                delay(500)
            }
        }
    }

    fun start() {
        val first = current ?: return
        val explicit = PlaybackQueue.startPositionMs
        val saved = container.library.progressFor(first.url)?.takeIf { it.isInProgress }?.positionMs
        load(index, explicit ?: saved ?: 0L)
        if (queue.size == 1 && first.url.startsWith("http")) expandQueueFromFolder(first)
    }

    /** A single file started from Continue Watching / search: find its siblings for "Next Episode". */
    private fun expandQueueFromFolder(item: PlayItem) {
        scope.launch {
            val plan = runCatching {
                container.playback.siblingsQueue(item.url, item.seriesTitle.ifBlank { item.title }, item.poster)
            }.getOrNull() ?: return@launch
            if (plan.queue.size > 1 && current?.url == item.url) {
                queue = plan.queue.map { if (it.url == item.url) item.copy(title = it.title, parentUrl = it.parentUrl) else it }
                index = plan.index
            }
        }
    }

    fun load(newIndex: Int, startPositionMs: Long = 0L) {
        val item = queue.getOrNull(newIndex) ?: return
        saveProgress()
        index = newIndex
        ended = false
        error = null
        isBuffering = true
        resumedFromMs = startPositionMs.takeIf { it > 10_000 }
        loadJob?.cancel()
        loadJob = scope.launch {
            // Side-load matching .srt / .vtt files from the video's folder (4 s budget).
            folderSubs = if (item.url.startsWith("http")) SubtitleFinder.find(item.url) else emptyList()
            prepare(item, startPositionMs)
        }
    }

    /** Folder subtitles found for the current video (kept so an online subtitle can be added later). */
    private var folderSubs: List<SubtitleSource> = emptyList()

    private fun prepare(item: PlayItem, positionMs: Long, playWhenReady: Boolean = true) {
        val online = container.onlineSubtitles.cached(item.url)
        hasOnlineSubtitle = online != null
        val configs = buildList {
            // A subtitle downloaded from OpenSubtitles for this video comes first and is the default.
            online?.let {
                add(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(it))
                        .setId("online")
                        .setMimeType("application/x-subrip")
                        .setLanguage("en")
                        .setLabel("English (OpenSubtitles)")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                )
            }
            folderSubs.forEach {
                add(
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.url))
                        .setMimeType(it.mimeType)
                        .setLanguage(it.language)
                        .setLabel(it.label)
                        .setSelectionFlags(if (online == null) C.SELECTION_FLAG_DEFAULT else 0)
                        .build()
                )
            }
        }
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(item.url))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.seriesTitle).build())
            .setSubtitleConfigurations(configs)
            .build()
        player.setMediaItem(mediaItem, positionMs)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    var hasOnlineSubtitle by mutableStateOf(false)
        private set

    /**
     * Re-prepares the current video with the newly downloaded online subtitle selected,
     * at the same position (a 1-2 s rebuffer).
     */
    fun attachOnlineSubtitle() {
        val item = current ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val wasPlaying = player.playWhenReady
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setPreferredTextLanguages("en")
            .build()
        prepare(item, pos, wasPlaying)
    }

    fun next() {
        if (hasNext) load(index + 1, container.library.progressFor(queue[index + 1].url)?.takeIf { it.isInProgress }?.positionMs ?: 0L)
    }

    fun previous() {
        if (hasPrevious) load(index - 1)
    }

    fun retry() {
        load(index, player.currentPosition.coerceAtLeast(0L))
    }

    fun togglePlay() {
        if (ended) {
            player.seekTo(0)
            ended = false
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekTo(ms: Long) {
        val d = durationMs
        player.seekTo(if (d > 0) ms.coerceIn(0, d - 1000) else ms.coerceAtLeast(0))
        positionMs = player.currentPosition
    }

    fun setPlaybackSpeed(value: Float) {
        speed = value
        player.setPlaybackSpeed(value)
        container.settings.playbackSpeed = value
    }

    // ------------------------------------------------------------------ tracks

    fun textOptions(): List<TrackOption> {
        val params = player.trackSelectionParameters
        val disabled = params.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val tracks = mutableListOf<TrackOption>()
        player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }.forEach { g ->
            for (i in 0 until g.length) {
                if (!g.isTrackSupported(i)) continue
                tracks += TrackOption(TrackLabels.subtitle(g.getTrackFormat(i)), !disabled && g.isTrackSelected(i), g, i)
            }
        }
        val labels = TrackLabels.disambiguate(tracks.map { it.label })
        return listOf(TrackOption("Off", disabled, null, -1)) + tracks.mapIndexed { n, t -> t.copy(label = labels[n]) }
    }

    fun audioOptions(): List<TrackOption> {
        val tracks = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }.flatMap { g ->
            (0 until g.length).filter { g.isTrackSupported(it, true) }.map { i ->
                TrackOption(TrackLabels.audio(g.getTrackFormat(i)), g.isTrackSelected(i), g, i)
            }
        }
        val labels = TrackLabels.disambiguate(tracks.map { it.label })
        return tracks.mapIndexed { n, t -> t.copy(label = labels[n]) }
    }

    fun selectText(option: TrackOption) {
        val b = player.trackSelectionParameters.buildUpon()
        if (option.group == null) {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
        }
        player.trackSelectionParameters = b.build()
    }

    fun selectAudio(option: TrackOption) {
        val group = option.group ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.index))
            .build()
    }

    // ------------------------------------------------------------------ progress

    fun saveProgress(finished: Boolean = false) {
        val item = current ?: return
        val dur = player.duration.takeIf { it > 0 } ?: durationMs
        if (dur <= 0) return
        val pos = if (finished) dur else player.currentPosition.coerceAtLeast(0L)
        if (pos < 5_000 && !finished) return
        container.library.saveProgress(
            WatchProgress(
                url = item.url, title = item.title, seriesTitle = item.seriesTitle, parentUrl = item.parentUrl,
                poster = item.poster, positionMs = pos, durationMs = dur, updatedAt = System.currentTimeMillis(),
            )
        )
    }

    fun release() {
        saveProgress()
        tickerJob?.cancel()
        loadJob?.cancel()
        player.removeListener(listener)
        player.release()
    }
}
