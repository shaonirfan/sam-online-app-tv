package com.shaonirfan.samonline.tv.data.repository

import com.shaonirfan.samonline.tv.data.local.LibraryStore
import com.shaonirfan.samonline.tv.data.model.Episode
import com.shaonirfan.samonline.tv.data.model.FolderContent
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.player.PlayItem

data class PlaybackPlan(val queue: List<PlayItem>, val index: Int, val positionMs: Long?)

/** Turns "Play" on a movie/series folder into a concrete episode queue + resume point. */
class PlaybackResolver(
    private val catalog: CatalogRepository,
    private val library: LibraryStore,
) {
    suspend fun resolve(item: MediaItem): Result<PlaybackPlan> {
        if (item.isVideo) return Result.success(siblingsQueue(item.url, NameCleaner.displayTitle(item.title), item.poster))
        val content = catalog.loadFolder(item.url, item.title, item.poster).getOrElse { return Result.failure(it) }
        val title = content as? FolderContent.Title
            ?: return Result.failure(IllegalStateException("Nothing playable in this folder"))
        val last = library.lastWatchedUnder(DirectoryScraper.folderUrl(item.url))
        if (last != null) {
            val plan = siblingsQueue(last.url, NameCleaner.displayTitle(title.item.title), title.item.poster)
            return Result.success(plan)
        }
        val firstEpisodes = when {
            title.files.isNotEmpty() -> title.files
            title.seasons.isNotEmpty() -> catalog.episodes(title.seasons.first()).getOrDefault(emptyList())
            else -> emptyList()
        }
        if (firstEpisodes.isEmpty()) return Result.failure(IllegalStateException("No video files found"))
        return Result.success(PlaybackPlan(toQueue(firstEpisodes, title.item), 0, null))
    }

    fun toQueue(episodes: List<Episode>, title: MediaItem): List<PlayItem> =
        episodes.map {
            PlayItem(
                url = it.url,
                title = it.title,
                seriesTitle = NameCleaner.displayTitle(title.title),
                parentUrl = DirectoryScraper.folderUrl(title.url),
                poster = title.poster,
            )
        }

    /** Queue = every video in the file's folder, starting at that file, resuming its saved position. */
    suspend fun siblingsQueue(videoUrl: String, seriesTitle: String, poster: String?): PlaybackPlan {
        val folder = DirectoryScraper.parentUrl(videoUrl)
        val videos = DirectoryScraper.list(folder).getOrNull().orEmpty()
            .filter { it.isVideo }
            .sortedWith(compareBy(CatalogRepository.naturalOrder) { it.name })
        val many = videos.size > 2
        // Season folders belong to the series folder one level up.
        val parent = if (catalog.isSeasonName(folder.trimEnd('/').substringAfterLast('/'))) DirectoryScraper.parentUrl(folder) else folder
        val queue = videos.mapIndexed { i, v ->
            PlayItem(
                url = v.url,
                title = if (many) NameCleaner.episodeLabel(v.name, i + 1) else NameCleaner.displayTitle(v.name),
                seriesTitle = seriesTitle,
                parentUrl = parent,
                poster = poster ?: catalog.cachedPoster(parent) ?: catalog.cachedPoster(folder),
            )
        }.ifEmpty {
            listOf(PlayItem(videoUrl, NameCleaner.displayTitle(videoUrl.substringAfterLast('/')), seriesTitle, parent, poster))
        }
        val index = queue.indexOfFirst { it.url == videoUrl }.coerceAtLeast(0)
        val saved = library.progressFor(videoUrl)?.takeIf { it.isInProgress }?.positionMs
        return PlaybackPlan(queue, index, saved)
    }
}
