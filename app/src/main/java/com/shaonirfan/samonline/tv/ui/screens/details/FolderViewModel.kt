package com.shaonirfan.samonline.tv.ui.screens.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaonirfan.samonline.tv.AppContainer
import com.shaonirfan.samonline.tv.data.download.EnqueueResult
import com.shaonirfan.samonline.tv.data.model.Episode
import com.shaonirfan.samonline.tv.data.model.FolderContent
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.SavedTitle
import com.shaonirfan.samonline.tv.data.model.WatchProgress
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.data.repository.PlaybackPlan
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FolderUiState {
    data object Loading : FolderUiState
    data class Error(val message: String) : FolderUiState
    data class Grid(val items: List<MediaItem>) : FolderUiState
    data class Details(
        val content: FolderContent.Title,
        val seasonIndex: Int = 0,
        val episodes: List<Episode> = emptyList(),
        val episodesLoading: Boolean = false,
        val related: List<MediaItem> = emptyList(),
    ) : FolderUiState
}

class FolderViewModel(
    private val container: AppContainer,
    val url: String,
    val title: String,
    private val posterHint: String?,
) : ViewModel() {

    private val _state = MutableStateFlow<FolderUiState>(FolderUiState.Loading)
    val state: StateFlow<FolderUiState> = _state.asStateFlow()

    val isSaved: StateFlow<Boolean> = container.library.myList
        .map { list -> list.any { it.url == DirectoryScraper.folderUrl(url) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.library.isSaved(DirectoryScraper.folderUrl(url)))

    val progress: StateFlow<Map<String, WatchProgress>> = container.library.progress

    private var seasonJob: Job? = null

    init {
        load()
    }

    fun load() {
        _state.value = FolderUiState.Loading
        viewModelScope.launch {
            container.catalog.loadFolder(url, title, posterHint)
                .onFailure { _state.value = FolderUiState.Error(it.localizedMessage ?: "Couldn't open this folder") }
                .onSuccess { content ->
                    when (content) {
                        is FolderContent.Grid -> _state.value = FolderUiState.Grid(content.items)
                        is FolderContent.Title -> {
                            // Start on the season of the last watched episode, if any.
                            val last = container.library.lastWatchedUnder(DirectoryScraper.folderUrl(url))
                            val seasonIndex = if (last != null) {
                                content.seasons.indexOfFirst { last.url.startsWith(DirectoryScraper.folderUrl(it.url)) }.coerceAtLeast(0)
                            } else 0
                            _state.value = FolderUiState.Details(content, seasonIndex, episodes = content.files)
                            if (content.seasons.isNotEmpty()) selectSeason(seasonIndex)
                            loadRelated()
                        }
                    }
                }
        }
    }

    fun selectSeason(index: Int) {
        val current = _state.value as? FolderUiState.Details ?: return
        val season = current.content.seasons.getOrNull(index) ?: return
        seasonJob?.cancel()
        _state.update { (it as? FolderUiState.Details)?.copy(seasonIndex = index, episodesLoading = true) ?: it }
        seasonJob = viewModelScope.launch {
            val eps = container.catalog.episodes(season).getOrDefault(emptyList())
            _state.update { (it as? FolderUiState.Details)?.copy(episodes = eps, episodesLoading = false) ?: it }
        }
    }

    private fun loadRelated() {
        viewModelScope.launch {
            val related = container.catalog.related(url)
            _state.update { (it as? FolderUiState.Details)?.copy(related = related) ?: it }
        }
    }

    fun toggleMyList(): Boolean {
        val details = _state.value as? FolderUiState.Details
        val item = details?.content?.item
        return container.library.toggle(
            SavedTitle(
                url = DirectoryScraper.folderUrl(url),
                title = item?.title ?: title,
                poster = item?.poster ?: posterHint,
                subtitle = item?.subtitle.orEmpty(),
                addedAt = System.currentTimeMillis(),
            )
        )
    }

    /** Resume point for the Play button: last watched episode under this title. */
    fun lastWatched(): WatchProgress? = container.library.lastWatchedUnder(DirectoryScraper.folderUrl(url))

    suspend fun playPlan(): Result<PlaybackPlan> {
        val details = _state.value as? FolderUiState.Details
            ?: return Result.failure(IllegalStateException("Not ready"))
        return container.playback.resolve(details.content.item)
    }

    fun planForEpisode(index: Int): PlaybackPlan? {
        val details = _state.value as? FolderUiState.Details ?: return null
        val queue = container.playback.toQueue(details.episodes, details.content.item)
        val episode = details.episodes.getOrNull(index) ?: return null
        val saved = container.library.progressFor(episode.url)?.takeIf { it.isInProgress }?.positionMs
        return PlaybackPlan(queue, index, saved)
    }

    fun download(episode: Episode): EnqueueResult = container.downloads.enqueue(episode.url, episode.url.substringAfterLast('/'))

    val downloadsEnabled: Boolean get() = container.settings.settings.value.downloadsEnabled
}
