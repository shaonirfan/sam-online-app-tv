package com.shaonirfan.samonline.tv.data.repository

import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.local.PosterStore
import com.shaonirfan.samonline.tv.data.model.Category
import com.shaonirfan.samonline.tv.data.model.Episode
import com.shaonirfan.samonline.tv.data.model.FolderContent
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.model.RemoteEntry
import com.shaonirfan.samonline.tv.data.model.Season
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.data.remote.Servers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class CatalogRepository(
    private val posters: PosterStore,
    private val rowsCache: JsonFileStore,
) {
    private val posterSemaphore = Semaphore(4)
    private val rowMemory = ConcurrentHashMap<String, List<MediaItem>>()

    private val seasonRegex = Regex(
        // Numbers are limited to 1-2 digits so "TV Series 2008–2013" or "Part 2024" never look like seasons.
        """(?i)(\bseason\b|\bs\d{1,2}\b|\bseries\s*\d{1,2}\b|\bpart\s*\d{1,2}\b|\bvol(ume)?\.?\s*\d{1,2}\b|\bbook\s*\d{1,2}\b|\bchapter\s*\d{1,2}\b)"""
    )
    /** Grouping folders: "TV Series ★ A — L", "(2024) 1080p", "1995-2000", "A-F". A spaced dash only, so "2008–2013" in a title doesn't count. */
    private val groupNameRegex = Regex("""(?i)(★|\s[—–-]\s|^[a-z0-9#]\s*[—–-]\s*[a-z0-9]$|^\(?\d{4}\)?(\s|$)|^\(?\d{4}\s*[—–-]\s*\d{4}\)?)""")
    private val leadingYearRegex = Regex("""^\(?(19\d{2}|20\d{2})""")

    // ------------------------------------------------------------------ posters

    fun cachedPoster(folderUrl: String): String? = posters.get(folderUrl)

    /** Resolves (and remembers) the poster for a folder, or for a video file's parent folder. */
    suspend fun posterFor(url: String): String? {
        val folder = if (DirectoryScraper.classify(url) != null) DirectoryScraper.parentUrl(url) else DirectoryScraper.folderUrl(url)
        posters.get(folder)?.let { return it }
        if (posters.isKnownMiss(folder)) return null
        return posterSemaphore.withPermit {
            posters.get(folder)?.let { return@withPermit it }
            val found = DirectoryScraper.findPoster(folder)
            posters.put(folder, found)
            found
        }
    }

    // ------------------------------------------------------------------ home rows

    /** Rows saved by the previous session, shown instantly on cold start. */
    fun cachedRows(): Map<String, List<MediaItem>> = runCatching {
        val o = JSONObject(rowsCache.read() ?: return emptyMap())
        buildMap {
            o.keys().forEach { id ->
                val arr = o.getJSONArray(id)
                put(id, (0 until arr.length()).map { i ->
                    val it = arr.getJSONObject(i)
                    MediaItem(
                        title = it.getString("t"), url = it.getString("u"),
                        poster = it.optString("p").ifBlank { null }, subtitle = it.optString("s"),
                        score = it.optLong("sc"),
                    )
                })
            }
        }
    }.getOrDefault(emptyMap())

    private fun persistRows() {
        val o = JSONObject()
        rowMemory.forEach { (id, items) ->
            val arr = JSONArray()
            items.forEach {
                arr.put(
                    JSONObject().put("t", it.title).put("u", it.url).put("p", it.poster ?: "")
                        .put("s", it.subtitle).put("sc", it.score)
                )
            }
            o.put(id, arr)
        }
        rowsCache.write(o.toString())
    }

    /**
     * Newest titles of a category, found the BeeGear way: walk year / A-Z / season
     * sub-folders (up to 3 levels) and keep folders that actually contain videos.
     */
    suspend fun categoryRow(category: Category, limit: Int = 20, force: Boolean = false): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (!force) rowMemory[category.id]?.let { return@withContext it }
            var items = scanCategory(category, limit)
            if (items.isEmpty()) {
                delay(450)
                items = scanCategory(category, limit)
            }
            if (items.isNotEmpty()) {
                rowMemory[category.id] = items
                persistRows()
            }
            items
        }

    private suspend fun scanCategory(category: Category, limit: Int): List<MediaItem> {
        val results = mutableListOf<MediaItem>()
        val seen = HashSet<String>()
        val top = DirectoryScraper.list(category.url, 15_000).getOrNull().orEmpty()

        fun add(entry: RemoteEntry, url: String, title: String, poster: String?, score: Long) {
            if (results.size < limit && seen.add(url)) {
                if (poster != null) posters.put(url, poster)
                results += MediaItem(title, url, poster ?: posters.get(url), category.name, score = score)
            }
        }

        val prioritized = top.filter { it.isFolder }
            .sortedWith(compareByDescending<RemoteEntry> { leadingYearRegex.containsMatchIn(it.name.trim()) }
                .thenByDescending { freshness(it) })
            .take(16)

        for (folder in prioritized) {
            if (results.size >= limit) break
            val inside = DirectoryScraper.list(folder.url, 14_000).getOrNull().orEmpty()
            val directPoster = DirectoryScraper.pickPoster(inside)
            val insideMax = inside.maxOfOrNull { it.lastModifiedMs } ?: 0L

            if (inside.any { it.isVideo } || (category.isSeries && hasSeasonStructure(inside))) {
                add(folder, folder.url, folder.name, directPoster, freshness(folder, insideMax))
                continue
            }

            for (child in inside.filter { it.isFolder }.sortedByDescending { freshness(it) }.take(14)) {
                if (results.size >= limit) break
                val childItems = DirectoryScraper.list(child.url, 13_000).getOrNull().orEmpty()
                val childPoster = DirectoryScraper.pickPoster(childItems)
                val childMax = maxOf(insideMax, childItems.maxOfOrNull { it.lastModifiedMs } ?: 0L)
                val childHasSeasons = hasSeasonStructure(childItems)

                if (childItems.any { it.isVideo } || childHasSeasons) {
                    add(child, child.url, child.name, childPoster, freshness(child, childMax))
                    continue
                }

                for (deep in childItems.filter { it.isFolder }.sortedByDescending { freshness(it) }.take(8)) {
                    if (results.size >= limit) break
                    val deepItems = DirectoryScraper.list(deep.url, 12_000).getOrNull().orEmpty()
                    if (deepItems.none { it.isVideo }) continue
                    val deepPoster = DirectoryScraper.pickPoster(deepItems) ?: childPoster
                    val deepMax = maxOf(childMax, deepItems.maxOfOrNull { it.lastModifiedMs } ?: 0L)
                    if (isSeasonName(deep.name)) {
                        add(child, child.url, child.name, childPoster ?: deepPoster, freshness(deep, deepMax))
                        break
                    }
                    add(deep, deep.url, deep.name, deepPoster, freshness(deep, deepMax))
                }
            }
        }
        return results.sortedByDescending { it.score }
    }

    private fun freshness(entry: RemoteEntry, nestedMax: Long = 0L): Long {
        val maxDate = maxOf(entry.lastModifiedMs, nestedMax)
        val year = NameCleaner.year(entry.name)?.toIntOrNull()?.takeIf { it in 1990..2035 }
        val yearBoost = if (year != null) (year - 1990).toLong() * 86_400_000L * 365L else 0L
        return if (maxDate > 0L) maxDate + yearBoost else yearBoost + entry.order * 1_000L
    }

    fun isSeasonName(name: String) = seasonRegex.containsMatchIn(NameCleaner.decode(name))

    /** At least half of the sub-folders look like seasons (so one "Part 2" movie can't turn a year folder into a series). */
    fun hasSeasonStructure(entries: List<RemoteEntry>): Boolean {
        val folders = entries.filter { it.isFolder }
        val seasonCount = folders.count { isSeasonName(it.name) }
        return seasonCount > 0 && seasonCount * 2 >= folders.size
    }

    // ------------------------------------------------------------------ folders

    /** Decides whether a folder is a browse grid, a movie or a series, and returns its content. */
    suspend fun loadFolder(url: String, fallbackTitle: String, posterHint: String?): Result<FolderContent> =
        withContext(Dispatchers.IO) {
            val folder = DirectoryScraper.folderUrl(url)
            val entries = DirectoryScraper.list(folder).getOrElse { return@withContext Result.failure(it) }
            val category = Servers.categoryFor(folder)
            val videos = entries.filter { it.isVideo }
            val folders = entries.filter { it.isFolder }
            val isGroupFolder = groupNameRegex.containsMatchIn(NameCleaner.decode(fallbackTitle)) || folder == category?.url
            var seasons = if (!isGroupFolder && hasSeasonStructure(entries)) folders.filter { isSeasonName(it.name) } else emptyList()

            // Series whose season folders have unusual names: peek inside the first sub-folder.
            if (videos.isEmpty() && seasons.isEmpty() && category?.isSeries == true && folders.size in 1..40 &&
                !isGroupFolder
            ) {
                val peek = DirectoryScraper.list(folders.first().url, 10_000).getOrNull().orEmpty()
                if (peek.any { it.isVideo }) seasons = folders
            }

            val title = NameCleaner.decode(fallbackTitle.ifBlank { folder.trimEnd('/').substringAfterLast('/') })
            if (videos.isEmpty() && seasons.isEmpty()) {
                val gridItems = folders.map {
                    MediaItem(it.name, it.url, posters.get(it.url), category?.name.orEmpty(), score = it.lastModifiedMs)
                }
                val hasYears = gridItems.count { NameCleaner.year(it.title) != null } > gridItems.size / 2
                val sorted = if (hasYears) {
                    gridItems.sortedWith(compareByDescending<MediaItem> { NameCleaner.year(it.title) ?: "" }
                        .thenBy(naturalOrder) { it.title })
                } else {
                    gridItems.sortedWith(compareBy(naturalOrder) { it.title })
                }
                return@withContext Result.success(FolderContent.Grid(sorted))
            }

            val poster = DirectoryScraper.pickPoster(entries) ?: posterHint ?: posters.get(folder)
                ?: posterFor(folder)
            if (poster != null) posters.put(folder, poster)

            val sortedVideos = videos.sortedWith(compareBy(naturalOrder) { it.name })
            val series = seasons.isNotEmpty() || sortedVideos.size > 2
            val files = sortedVideos.mapIndexed { i, e ->
                Episode(
                    title = if (series) NameCleaner.episodeLabel(e.name, i + 1) else NameCleaner.displayTitle(e.name),
                    url = e.url, index = i, sizeLabel = e.sizeLabel,
                )
            }
            Result.success(
                FolderContent.Title(
                    item = MediaItem(title, folder, poster, category?.name.orEmpty()),
                    seasons = seasons.sortedWith(compareBy(naturalOrder) { it.name }).map { Season(NameCleaner.decode(it.name), it.url) },
                    files = files,
                )
            )
        }

    suspend fun episodes(season: Season): Result<List<Episode>> = withContext(Dispatchers.IO) {
        DirectoryScraper.list(season.url).map { entries ->
            entries.filter { it.isVideo }.sortedWith(compareBy(naturalOrder) { it.name }).mapIndexed { i, e ->
                Episode(NameCleaner.episodeLabel(e.name, i + 1), e.url, i, e.sizeLabel)
            }
        }
    }

    /** Sibling titles in the same parent folder ("More like this"). */
    suspend fun related(url: String, limit: Int = 20): List<MediaItem> = withContext(Dispatchers.IO) {
        val folder = DirectoryScraper.folderUrl(url)
        val category = Servers.categoryFor(folder) ?: return@withContext emptyList()
        if (folder == category.url) return@withContext emptyList()
        val parent = DirectoryScraper.parentUrl(folder)
        DirectoryScraper.list(parent).getOrNull().orEmpty()
            .filter { it.isFolder && DirectoryScraper.folderUrl(it.url) != folder }
            .shuffled(java.util.Random(folder.hashCode().toLong()))
            .take(limit)
            .map { MediaItem(it.name, it.url, posters.get(it.url), category.name) }
    }

    companion object {
        /** "Episode 2" < "Episode 10". */
        val naturalOrder: Comparator<String> = Comparator { a, b ->
            val ra = Regex("""\d+|\D+""").findAll(a.lowercase()).map { it.value }.toList()
            val rb = Regex("""\d+|\D+""").findAll(b.lowercase()).map { it.value }.toList()
            for (i in 0 until minOf(ra.size, rb.size)) {
                val x = ra[i]
                val y = rb[i]
                val c = if (x[0].isDigit() && y[0].isDigit()) {
                    val nx = x.trimStart('0')
                    val ny = y.trimStart('0')
                    if (nx.length != ny.length) nx.length - ny.length else nx.compareTo(ny)
                } else x.compareTo(y)
                if (c != 0) return@Comparator c
            }
            ra.size - rb.size
        }
    }
}
