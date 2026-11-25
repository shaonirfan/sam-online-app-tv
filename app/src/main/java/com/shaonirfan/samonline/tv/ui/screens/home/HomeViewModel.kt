package com.shaonirfan.samonline.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaonirfan.samonline.tv.AppContainer
import com.shaonirfan.samonline.tv.data.model.Category
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.model.WatchProgress
import com.shaonirfan.samonline.tv.data.remote.Servers
import com.shaonirfan.samonline.tv.data.repository.PlaybackPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RowState(
    val category: Category,
    val items: List<MediaItem> = emptyList(),
    val loading: Boolean = true,
    val failed: Boolean = false,
)

data class HomeUiState(
    val hero: List<MediaItem> = emptyList(),
    val rows: List<RowState> = Servers.categories.map { RowState(it) },
) {
    val allFailed: Boolean get() = rows.all { it.failed && it.items.isEmpty() }
}

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val continueWatching: StateFlow<List<WatchProgress>> = container.library.progress
        .map { container.library.continueWatching(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.library.continueWatching(container.library.progress.value))

    val myList: StateFlow<List<MediaItem>> = container.library.myList
        .map { list -> list.map { MediaItem(it.title, it.url, it.poster, it.subtitle) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val heroAutoRotate: Boolean get() = container.settings.settings.value.heroAutoRotate

    init {
        // Rows from the previous session appear instantly, then refresh from the servers.
        val cached = container.catalog.cachedRows()
        if (cached.isNotEmpty()) {
            _state.update { s ->
                s.copy(rows = s.rows.map { r -> cached[r.category.id]?.let { r.copy(items = it) } ?: r })
            }
            buildHeroIfNeeded()
        }
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        Servers.categories.forEach { category ->
            viewModelScope.launch {
                _state.update { s -> s.copy(rows = s.rows.map { if (it.category.id == category.id) it.copy(loading = true, failed = false) else it }) }
                val items = runCatching { container.catalog.categoryRow(category, force = force) }.getOrDefault(emptyList())
                _state.update { s ->
                    s.copy(rows = s.rows.map {
                        if (it.category.id != category.id) it
                        else if (items.isNotEmpty()) it.copy(items = items, loading = false, failed = false)
                        else it.copy(loading = false, failed = true)
                    })
                }
                buildHeroIfNeeded()
            }
        }
    }

    /** Hero = the newest 1–2 titles of each category, fixed once built so the carousel doesn't jump. */
    private fun buildHeroIfNeeded() {
        val s = _state.value
        if (s.hero.isNotEmpty()) return
        val ready = s.rows.filter { it.items.isNotEmpty() }
        if (ready.size < 3 && s.rows.any { it.loading && it.items.isEmpty() }) return
        val picks = ready.flatMap { row -> row.items.filter { it.poster != null }.take(2).ifEmpty { row.items.take(1) } }
            .distinctBy { NameCleaner.displayTitle(it.title).lowercase() }
            .take(8)
        if (picks.isNotEmpty()) _state.update { it.copy(hero = picks) }
    }

    suspend fun resolvePlay(item: MediaItem): Result<PlaybackPlan> = container.playback.resolve(item)

    suspend fun resume(progress: WatchProgress): PlaybackPlan =
        container.playback.siblingsQueue(progress.url, progress.seriesTitle, progress.poster)

    fun removeFromContinue(progress: WatchProgress) = container.library.removeProgress(progress.url)
}
