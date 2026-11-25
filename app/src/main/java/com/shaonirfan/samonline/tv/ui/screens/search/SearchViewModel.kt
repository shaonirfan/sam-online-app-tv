package com.shaonirfan.samonline.tv.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shaonirfan.samonline.tv.AppContainer
import com.shaonirfan.samonline.tv.TestHooks
import com.shaonirfan.samonline.tv.TestLog
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.repository.IndexState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

enum class SearchFilter(val label: String) { ALL("All"), MOVIES("Movies"), SERIES("TV Shows") }

data class SearchResults(
    val query: String = "",
    val items: List<MediaItem> = emptyList(),
    val suggestions: List<String> = emptyList(),
) {
    fun filtered(filter: SearchFilter): List<MediaItem> = when (filter) {
        SearchFilter.ALL -> items
        SearchFilter.MOVIES -> items.filter { !it.isSeries }
        SearchFilter.SERIES -> items.filter { it.isSeries }
    }

    fun count(filter: SearchFilter) = filtered(filter).size
}

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SearchViewModel(private val container: AppContainer) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val indexState: StateFlow<IndexState> = container.search.state

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    fun setFilter(f: SearchFilter) {
        _filter.value = f
    }

    /**
     * Debounced search over the local index. While the very first index is still being
     * built, results refresh as more titles arrive (every 500 new entries).
     */
    val results: StateFlow<SearchResults> = combine(
        _query.debounce(220).distinctUntilChanged(),
        indexState.map { it.count / 500 to it.ready }.distinctUntilChanged(),
    ) { q, _ -> q }
        .mapLatest { q ->
            if (q.isBlank()) {
                SearchResults()
            } else {
                val items = container.search.search(q)
                SearchResults(q, items, container.search.suggestions(q, items)).also { r ->
                    TestLog.mark(
                        "search", "q" to q, "count" to items.size,
                        "movies" to r.count(SearchFilter.MOVIES), "series" to r.count(SearchFilter.SERIES),
                        "first" to items.take(3).joinToString("|") { it.displayTitle + "/" + (if (it.isSeries) "tv" else "movie") },
                        "suggestions" to r.suggestions.take(4).joinToString("|"),
                        "indexed" to indexState.value.count,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchResults())

    init {
        container.search.warmUp()
        TestHooks.pendingSearch?.let { _query.value = it; TestHooks.pendingSearch = null }
    }

    fun type(c: Char) {
        if (_query.value.length < 60) _query.value += c
    }

    fun backspace() {
        _query.value = _query.value.dropLast(1)
    }

    fun clear() {
        _query.value = ""
    }

    fun set(text: String) {
        _query.value = text.take(60)
    }
}
