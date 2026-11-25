package com.shaonirfan.samonline.tv.data.repository

import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.local.PosterStore
import com.shaonirfan.samonline.tv.data.model.Category
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.model.SearchIndexEntry
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.data.remote.Servers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class IndexState(
    val ready: Boolean = false,
    val building: Boolean = false,
    val count: Int = 0,
    val builtAt: Long = 0L,
)

/**
 * Library-wide search. The first search crawls each category's grouping folders
 * (year / A–Z / collection folders, up to 3 levels) and stores a title index on disk,
 * so every later search is an instant in-memory filter. The index refreshes every 24h.
 */
class SearchRepository(
    private val store: JsonFileStore,
    private val posters: PosterStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries: MutableList<SearchIndexEntry> = Collections.synchronizedList(mutableListOf())
    private var buildJob: Job? = null
    @Volatile private var loaded = false

    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private val groupRegex = Regex(
        """(?i)(★|\s[—–-]\s|^\(?\d{4}\)?(\s|$|-)|^\(?\d{4}\s*[—–-]\s*\d{4}\)?|^[a-z0-9#]\s*[—–-]\s*[a-z0-9]$|^[a-z0-9#]$|^(tv\s+)?(movies|series|collection|dubbed|1080p|720p|4k|hindi|english|bangla|korean|anime|cartoon)\b)"""
    )

    /** Loads the cached index and refreshes it in the background when stale/missing. */
    fun warmUp() {
        scope.launch {
            ensureLoaded()
            val age = System.currentTimeMillis() - _state.value.builtAt
            if (!_state.value.ready || age > 24L * 60 * 60 * 1000) rebuild()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            runCatching {
                val o = JSONObject(store.read() ?: return@runCatching)
                val arr = o.getJSONArray("items")
                val list = ArrayList<SearchIndexEntry>(arr.length())
                for (i in 0 until arr.length()) {
                    val it = arr.getJSONArray(i)
                    val title = it.getString(0)
                    list += entry(title, it.getString(1), it.getString(2), it.getInt(3) == 1)
                }
                entries.clear()
                entries.addAll(list)
                _state.value = IndexState(ready = list.isNotEmpty(), count = list.size, builtAt = o.optLong("builtAt"))
            }
            loaded = true
        }
    }

    fun rebuild() {
        if (buildJob?.isActive == true) return
        buildJob = scope.launch {
            ensureLoaded()
            val hadIndex = _state.value.ready
            _state.value = _state.value.copy(building = true)
            val fresh = Collections.synchronizedList(mutableListOf<SearchIndexEntry>())
            // If there is no index yet, searches read the one being built as it grows.
            if (!hadIndex) entries.clear()
            val target = if (hadIndex) fresh else entries

            coroutineScope {
                Servers.categories.map { cat -> async { crawl(cat, cat.url, 0, target) } }.awaitAll()
            }
            val result = target.distinctBy { it.url }
            if (result.isNotEmpty()) {
                synchronized(entries) {
                    entries.clear()
                    entries.addAll(result)
                }
                persist(result)
            }
            _state.value = IndexState(ready = entries.isNotEmpty(), building = false, count = entries.size, builtAt = System.currentTimeMillis())
        }
    }

    private suspend fun crawl(category: Category, url: String, depth: Int, out: MutableList<SearchIndexEntry>) {
        val listing = DirectoryScraper.list(url, 15_000).getOrNull() ?: return
        val children = mutableListOf<String>()
        for (e in listing) {
            when {
                e.isFolder -> {
                    val name = NameCleaner.decode(e.name)
                    val isGroup = groupRegex.containsMatchIn(name) || name.length <= 2
                    if (isGroup && depth < 2) {
                        children += e.url
                    } else {
                        out += entry(e.name, e.url, category.name, false)
                    }
                }
                e.isVideo -> out += entry(e.name, e.url, category.name, true)
                else -> Unit
            }
        }
        if (out === entries) _state.value = _state.value.copy(count = entries.size)
        coroutineScope {
            children.map { child -> async { crawl(category, child, depth + 1, out) } }.awaitAll()
        }
    }

    private fun persist(list: List<SearchIndexEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONArray().put(it.title).put(it.url).put(it.category).put(if (it.isVideo) 1 else 0)) }
        store.write(JSONObject().put("builtAt", System.currentTimeMillis()).put("items", arr).toString())
    }

    private fun entry(title: String, url: String, category: String, isVideo: Boolean) = SearchIndexEntry(
        title = title,
        url = url,
        category = category,
        isVideo = isVideo,
        key = NameCleaner.normalizeForSearch(title),
        titleKey = NameCleaner.normalizeForSearch(NameCleaner.displayTitle(title)),
        isSeries = Servers.categories.firstOrNull { it.name == category }?.isSeries == true,
    )

    /**
     * Ranks on the cleaned title, so "friends" puts the series *Friends* first, then
     * whole-word matches ("Friends with Benefits"), then partial words ("Friendsgiving").
     * Folders beat loose video files; ties go to the shorter title, then the newer year.
     */
    suspend fun search(query: String, limit: Int = 150): List<MediaItem> = withContext(Dispatchers.Default) {
        ensureLoaded()
        val q = NameCleaner.normalizeForSearch(query)
        if (q.isBlank()) return@withContext emptyList()
        val tokens = q.split(' ').filter { it.isNotBlank() }
        val snapshot = synchronized(entries) { entries.toList() }
        snapshot.asSequence()
            .mapNotNull { e ->
                val t = e.titleKey
                val rank = when {
                    t == q -> 0
                    t.startsWith("$q ") -> 1
                    t.contains(" $q ") || t.endsWith(" $q") -> 2
                    t.startsWith(q) -> 3
                    t.contains(" $q") -> 4
                    t.contains(q) -> 5
                    tokens.size > 1 && tokens.all { tok -> t.contains(tok) } -> 6
                    e.key.contains(q) -> 7
                    tokens.size > 1 && tokens.all { tok -> e.key.contains(tok) } -> 8
                    else -> return@mapNotNull null
                }
                Triple(rank + if (e.isVideo) 10 else 0, e, NameCleaner.year(e.title) ?: "")
            }
            .sortedWith(
                compareBy<Triple<Int, SearchIndexEntry, String>> { it.first }
                    .thenBy { it.second.titleKey.length }
                    .thenByDescending { it.third }
            )
            .distinctBy { it.second.titleKey + "|" + it.second.category }
            .take(limit)
            .map { (_, e, _) ->
                val posterKey = if (e.isVideo) DirectoryScraper.parentUrl(e.url) else e.url
                MediaItem(e.title, e.url, posters.get(posterKey), e.category, isVideo = e.isVideo, isSeries = e.isSeries)
            }
            .toList()
    }

    /** Title suggestions for the typed prefix: titles whose words start with it, best first. */
    fun suggestions(query: String, results: List<MediaItem>, limit: Int = 6): List<String> {
        val q = NameCleaner.normalizeForSearch(query)
        if (q.isBlank()) return emptyList()
        return results.asSequence()
            .filter { !it.isVideo }
            .map { it.displayTitle }
            .filter { t ->
                val n = NameCleaner.normalizeForSearch(t)
                n.startsWith(q) || n.contains(" $q")
            }
            .distinctBy { it.lowercase() }
            .take(limit)
            .toList()
    }
}
