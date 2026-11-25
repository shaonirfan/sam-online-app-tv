package com.shaonirfan.samonline.tv.data.remote

import com.shaonirfan.samonline.tv.data.model.NameCleaner
import kotlinx.coroutines.withTimeoutOrNull

data class SubtitleSource(
    val url: String,
    val mimeType: String,
    val label: String,
    val language: String,
)

/**
 * Finds .srt / .vtt / .ass files next to a video (BeeGear's findSameFolderSubtitles):
 * names that match the video are used; if nothing matches and the folder holds a
 * single video, every subtitle in the folder (and in a "Subs" sub-folder) is offered.
 */
object SubtitleFinder {

    fun mimeFor(name: String): String? {
        val p = name.substringBefore('?').lowercase()
        return when {
            p.endsWith(".srt") -> "application/x-subrip"
            p.endsWith(".vtt") -> "text/vtt"
            p.endsWith(".ass") || p.endsWith(".ssa") -> "text/x-ssa"
            else -> null
        }
    }

    private val languages = mapOf(
        "en" to "English", "eng" to "English", "english" to "English",
        "bn" to "Bangla", "ben" to "Bangla", "bangla" to "Bangla", "bengali" to "Bangla",
        "hi" to "Hindi", "hin" to "Hindi", "hindi" to "Hindi",
        "ar" to "Arabic", "es" to "Spanish", "spa" to "Spanish", "fr" to "French",
        "ko" to "Korean", "kor" to "Korean", "ja" to "Japanese", "jpn" to "Japanese",
        "zh" to "Chinese", "chi" to "Chinese",
    )

    private fun base(name: String) = NameCleaner.decode(name).substringBeforeLast('.')
        .lowercase().replace(Regex("[._\\-\\s]+"), " ").trim()

    suspend fun find(videoUrl: String, timeoutMs: Long = 4_000): List<SubtitleSource> =
        withTimeoutOrNull(timeoutMs) {
            val folder = DirectoryScraper.parentUrl(videoUrl)
            val entries = DirectoryScraper.list(folder, 8_000).getOrNull().orEmpty()
            val videoBase = base(videoUrl.substringAfterLast('/'))
            var subs = entries.filter { it.isSubtitle }
            // Common layout: a "Subs" / "Subtitles" sub-folder.
            entries.firstOrNull { it.isFolder && it.name.contains("sub", ignoreCase = true) }?.let { subFolder ->
                subs = subs + DirectoryScraper.list(subFolder.url, 6_000).getOrNull().orEmpty().filter { it.isSubtitle }
            }
            val matching = subs.filter {
                val b = base(it.name)
                b == videoBase || videoBase.startsWith(b) || b.startsWith(videoBase)
            }
            val singleVideo = entries.count { it.isVideo } <= 1
            val chosen = matching.ifEmpty { if (singleVideo) subs else emptyList() }
            chosen.mapIndexedNotNull { i, e ->
                val mime = mimeFor(e.name) ?: return@mapIndexedNotNull null
                val tokens = base(e.name).split(' ')
                val lang = tokens.asReversed().firstNotNullOfOrNull { languages[it] }
                SubtitleSource(
                    url = e.url,
                    mimeType = mime,
                    label = lang ?: if (chosen.size == 1) "Subtitles" else "Subtitles ${i + 1}",
                    language = languages.entries.firstOrNull { it.value == lang }?.key ?: "und",
                )
            }.distinctBy { it.url }
        } ?: emptyList()
}
