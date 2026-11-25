package com.shaonirfan.samonline.tv.player

data class PlayItem(
    val url: String,
    val title: String,
    /** Series or movie folder name, shown above the episode title. */
    val seriesTitle: String,
    /** The title folder this file belongs to (groups Continue Watching per title). */
    val parentUrl: String,
    val poster: String?,
)

/**
 * Hands the episode list from the details screen to the player without squeezing it
 * into navigation arguments.
 */
object PlaybackQueue {
    var items: List<PlayItem> = emptyList()
        private set
    var startIndex: Int = 0
        private set
    var startPositionMs: Long? = null
        private set

    fun set(queue: List<PlayItem>, index: Int, positionMs: Long?) {
        items = queue
        startIndex = index.coerceIn(0, (queue.size - 1).coerceAtLeast(0))
        startPositionMs = positionMs
    }
}
