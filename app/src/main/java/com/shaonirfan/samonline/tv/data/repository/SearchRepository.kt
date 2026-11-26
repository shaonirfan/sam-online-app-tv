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
import kotlinx.coroutines.sync.withPermit
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
    /** The v1.0.0-1.0.5 JSON index; read once, then saved in the fast format. */
    private val legacyStore: JsonFileStore? = null,
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
    /**
     * At app start the index build waits [delayMs] so the Home rows get the network first
     * (opening Search calls this with no delay).
     */
    fun warmUp(delayMs: Long = 0L) {
        scope.launch {
            // The saved index is loaded right away (fast, no network) so the first search is instant;
            // only the refresh crawl waits, so the Home rows get the network first.
            ensureLoaded()
            val age = System.currentTimeMillis() - _state.value.builtAt
            if (_state.value.ready && age <= REFRESH_AFTER_MS) return@launch
            if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
            rebuild()
        }
    }

    private fun ensureLoaded() {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val started = System.currentTimeMillis()
            var list = runCatching { store.read()?.let(::parseTsv) }.getOrNull()
            var builtAt = list?.second ?: 0L
            val legacy = legacyStore
            if (list == null && legacy != null) {
                // One-time upgrade from the old JSON file (slower: titles are cleaned again).
                list = runCatching { legacy.read()?.let(::parseLegacyJson) }.getOrNull()
                builtAt = list?.second ?: 0L
                list?.first?.takeIf { it.isNotEmpty() }?.let { persist(it, builtAt); legacy.delete() }
            }
            val items = list?.first.orEmpty()
            entries.clear()
            entries.addAll(items)
            _state.value = IndexState(ready = items.isNotEmpty(), count = items.size, builtAt = builtAt)
            loaded = true
            com.shaonirfan.samonline.tv.TestLog.mark(
                "index_loaded", "titles" to items.size, "ms" to System.currentTimeMillis() - started,
                "age_h" to if (builtAt > 0) (System.currentTimeMillis() - builtAt) / 3_600_000 else -1,
            )
        }
    }

    /**
     * Index file: a header line "SAMIDX2\t<builtAt>", then one entry per line with every
     * field pre-computed (raw title, url, category, isVideo, key, titleKey, isSeries), so
     * loading is a plain split - no regex cleaning of ~30k titles on every app start.
     */
    private fun parseTsv(text: String): Pair<List<SearchIndexEntry>, Long>? {
        val lines = text.lineSequence().iterator()
        if (!lines.hasNext()) return null
        val header = lines.next().split('\t')
        if (header.firstOrNull() != TSV_MAGIC) return null
        val builtAt = header.getOrNull(1)?.toLongOrNull() ?: 0L
        val out = ArrayList<SearchIndexEntry>(40_000)
        while (lines.hasNext()) {
            val f = lines.next().split('\t')
            if (f.size < 7) continue
            out += SearchIndexEntry(f[0], f[1], f[2], f[3] == "1", f[4], f[5], f[6] == "1")
        }
        return out to builtAt
    }

    private fun parseLegacyJson(text: String): Pair<List<SearchIndexEntry>, Long> {
        val o = JSONObject(text)
        val arr = o.getJSONArray("items")
        val list = ArrayList<SearchIndexEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val it = arr.getJSONArray(i)
            list += entry(it.getString(0), it.getString(1), it.getString(2), it.getInt(3) == 1)
        }
        return list to o.optLong("builtAt")
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
            com.shaonirfan.samonline.tv.TestLog.mark("index_ready", "titles" to entries.size)
        }
    }

    /** The crawl uses at most 3 of the scraper's 8 connections so browsing stays fast meanwhile. */
    private val crawlPermits = kotlinx.coroutines.sync.Semaphore(3)

    private suspend fun crawl(category: Category, url: String, depth: Int, out: MutableList<SearchIndexEntry>) {
        val listing = crawlPermits.withPermit { DirectoryScraper.list(url, 15_000).getOrNull() } ?: return
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

    private fun persist(list: List<SearchIndexEntry>, builtAt: Long = System.currentTimeMillis()) {
        fun clean(v: String) = v.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')
        val sb = StringBuilder(list.size * 160)
        sb.append(TSV_MAGIC).append('\t').append(builtAt).append('\n')
        list.forEach {
            sb.append(clean(it.title)).append('\t').append(clean(it.url)).append('\t').append(clean(it.category)).append('\t')
                .append(if (it.isVideo) '1' else '0').append('\t').append(clean(it.key)).append('\t')
                .append(clean(it.titleKey)).append('\t').append(if (it.isSeries) '1' else '0').append('\n')
        }
        store.write(sb.toString())
    }

    companion object {
        private const val TSV_MAGIC = "SAMIDX2"
        /** The library index is refreshed in the background once it is a day old. */
        private const val REFRESH_AFTER_MS = 24L * 60 * 60 * 1000
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
