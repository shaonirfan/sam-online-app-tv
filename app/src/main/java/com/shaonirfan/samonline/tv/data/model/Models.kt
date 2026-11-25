package com.shaonirfan.samonline.tv.data.model

/** A top-level library section on one of the DHAKA-FLIX servers. */
data class Category(
    val id: String,
    val name: String,
    val url: String,
    val isSeries: Boolean = false,
)

enum class EntryType { FOLDER, VIDEO, IMAGE, SUBTITLE }

/** One link scraped from an autoindex / h5ai directory listing. */
data class RemoteEntry(
    val name: String,
    val url: String,
    val type: EntryType,
    val lastModifiedMs: Long = 0L,
    val sizeLabel: String = "",
    val order: Int = 0,
) {
    val isFolder get() = type == EntryType.FOLDER
    val isVideo get() = type == EntryType.VIDEO
    val isImage get() = type == EntryType.IMAGE
    val isSubtitle get() = type == EntryType.SUBTITLE
}

/**
 * Something the user can browse to and see a poster for: a movie folder,
 * a series folder, a grouping folder (year / A-Z) or a single video file.
 */
data class MediaItem(
    val title: String,
    val url: String,
    val poster: String? = null,
    val subtitle: String = "",
    val isVideo: Boolean = false,
    val score: Long = 0L,
    /** From a series category (TV & Web Series, Anime, Korean); used by the search filter. */
    val isSeries: Boolean = false,
) {
    val displayTitle: String get() = NameCleaner.displayTitle(title)
    val year: String? get() = NameCleaner.year(title)
}

/** An entry in a season / file list on the details screen. */
data class Episode(
    val title: String,
    val url: String,
    val index: Int,
    val sizeLabel: String = "",
)

data class Season(
    val name: String,
    val url: String,
)

/** What a folder turned out to be once its listing was inspected. */
sealed interface FolderContent {
    /** Only sub-folders and no seasons: a grid to browse further. */
    data class Grid(val items: List<MediaItem>) : FolderContent

    /** A playable title (movie or series). */
    data class Title(
        val item: MediaItem,
        val seasons: List<Season>,
        /** Videos directly inside this folder (movie files or single-season episodes). */
        val files: List<Episode>,
    ) : FolderContent {
        val isSeries: Boolean get() = seasons.isNotEmpty() || files.size > 2
    }
}

data class WatchProgress(
    val url: String,
    val title: String,
    val seriesTitle: String,
    val parentUrl: String,
    val poster: String?,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
) {
    val fraction: Float
        get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val isInProgress: Boolean get() = fraction in 0.02f..0.95f
}

data class SavedTitle(
    val url: String,
    val title: String,
    val poster: String?,
    val subtitle: String,
    val addedAt: Long,
)

data class SearchIndexEntry(
    val title: String,
    val url: String,
    val category: String,
    val isVideo: Boolean,
    /** Pre-normalised raw name (with year, quality...) for token matching. */
    val key: String,
    /** Pre-normalised cleaned title ("friends") for ranking and suggestions. */
    val titleKey: String,
    val isSeries: Boolean,
)
