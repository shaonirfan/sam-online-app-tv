package com.shaonirfan.samonline.tv.data.remote

import com.shaonirfan.samonline.tv.data.model.EntryType
import com.shaonirfan.samonline.tv.data.model.RemoteEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.URL
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

/**
 * Scrapes autoindex / h5ai directory listings with Jsoup (logic ported from BeeGear's
 * scanFolder, with stricter link filtering): every <a href> that stays inside the
 * current folder is classified as folder / video / image / subtitle.
 */
object DirectoryScraper {

    val videoExtensions = listOf(".mp4", ".mkv", ".avi", ".mov", ".webm", ".m4v", ".ts", ".flv", ".wmv")
    val imageExtensions = listOf(".jpg", ".jpeg", ".png", ".webp", ".bmp")
    val subtitleExtensions = listOf(".srt", ".vtt", ".ass", ".ssa")

    private const val CACHE_TTL_MS = 10 * 60 * 1000L
    private data class CacheEntry(val at: Long, val entries: List<RemoteEntry>)

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val network = Semaphore(8)

    private val datePatterns = listOf(
        "yyyy-MM-dd HH:mm", "yyyy-MM-dd", "dd-MMM-yyyy HH:mm", "dd-MMM-yyyy",
        "dd/MMM/yyyy HH:mm", "MM/dd/yyyy hh:mm:ss a", "MM/dd/yyyy HH:mm:ss", "MM/dd/yyyy",
    )
    private val dateRegex = Regex(
        """(\d{4}-\d{2}-\d{2}(?:\s+\d{2}:\d{2})?|\d{2}-[A-Za-z]{3}-\d{4}(?:\s+\d{2}:\d{2})?|\d{2}/[A-Za-z]{3}/\d{4}(?:\s+\d{2}:\d{2})?|\d{2}/\d{2}/\d{4}(?:\s+\d{1,2}:\d{2}(?::\d{2})?(?:\s*[APap][Mm])?)?)"""
    )
    private val sizeRegex = Regex("""(?i)\b(\d+(?:\.\d+)?)\s?([KMGT])(?:i?B)?\b""")

    fun folderUrl(url: String): String = if (url.endsWith("/")) url else "$url/"

    fun parentUrl(url: String): String {
        val trimmed = url.trimEnd('/')
        return trimmed.substringBeforeLast('/') + "/"
    }

    fun classify(path: String): EntryType? {
        val p = path.lowercase()
        return when {
            videoExtensions.any { p.endsWith(it) } -> EntryType.VIDEO
            imageExtensions.any { p.endsWith(it) } -> EntryType.IMAGE
            subtitleExtensions.any { p.endsWith(it) } -> EntryType.SUBTITLE
            else -> null
        }
    }

    fun cached(url: String): List<RemoteEntry>? {
        val hit = cache[folderUrl(url)] ?: return null
        return if (System.currentTimeMillis() - hit.at < CACHE_TTL_MS) hit.entries else null
    }

    /** Lists one folder. Results are cached in memory for 10 minutes. */
    suspend fun list(url: String, timeoutMs: Int = 15_000, useCache: Boolean = true): Result<List<RemoteEntry>> =
        withContext(Dispatchers.IO) {
            val base = folderUrl(url)
            if (useCache) cached(base)?.let { return@withContext Result.success(it) }
            runCatching {
                val doc = network.withPermit {
                    Jsoup.connect(base)
                        .userAgent(Servers.USER_AGENT)
                        .timeout(timeoutMs)
                        .ignoreContentType(true)
                        .followRedirects(true)
                        .get()
                }
                val baseUrl = URL(base)
                val basePath = decodePath(baseUrl.path)
                var seq = 0
                val entries = doc.select("a[href]").mapNotNull { link ->
                    val href = link.attr("href").trim()
                    if (href.isBlank() || href.startsWith("#") || href.startsWith("?") ||
                        href == "../" || href == "./" || href.startsWith("javascript:") ||
                        href.startsWith("mailto:")
                    ) return@mapNotNull null
                    val address = link.absUrl("href").substringBefore('#')
                    if (address.isBlank() || address.contains('?')) return@mapNotNull null
                    val parsed = runCatching { URL(address) }.getOrNull() ?: return@mapNotNull null
                    if (parsed.host != baseUrl.host) return@mapNotNull null
                    val path = decodePath(parsed.path)
                    // Only keep links that point *inside* this folder (drops parent links,
                    // breadcrumbs, sort links and h5ai's own assets).
                    if (!path.startsWith(basePath) || path.trimEnd('/') == basePath.trimEnd('/')) return@mapNotNull null
                    if (path.contains("/_h5ai/")) return@mapNotNull null

                    val type = classify(path)
                        ?: if (href.endsWith("/") || parsed.path.endsWith("/")) EntryType.FOLDER else return@mapNotNull null

                    val name = link.text().trim().takeIf { it.isNotBlank() && !it.equals("Parent Directory", true) }
                        ?: path.trimEnd('/').substringAfterLast('/')
                    val rowText = (link.closest("tr") ?: link.parent())?.text().orEmpty()
                    RemoteEntry(
                        name = name.trimEnd('/'),
                        url = address,
                        type = type,
                        lastModifiedMs = parseDate(rowText),
                        sizeLabel = if (type == EntryType.FOLDER) "" else parseSize(rowText.removePrefix(name)),
                        order = seq++,
                    )
                }.distinctBy { it.url.trimEnd('/') }
                    .sortedWith(compareByDescending<RemoteEntry> { it.isFolder }.thenBy { it.name.lowercase() })
                cache[base] = CacheEntry(System.currentTimeMillis(), entries)
                entries
            }
        }

    /** A folder's poster is the first image found inside it, up to 3 levels deep (BeeGear's rule). */
    suspend fun findPoster(folderUrl: String, maxDepth: Int = 3): String? {
        var current = folderUrl(folderUrl)
        repeat(maxDepth) {
            val entries = list(current, 9_000).getOrNull().orEmpty()
            pickPoster(entries)?.let { return it }
            current = entries.firstOrNull { it.isFolder }?.url ?: return null
        }
        return null
    }

    /** Prefers files named poster/cover/folder, otherwise the first image. */
    fun pickPoster(entries: List<RemoteEntry>): String? {
        val images = entries.filter { it.isImage }
        if (images.isEmpty()) return null
        return (images.firstOrNull { img ->
            val n = img.name.lowercase()
            listOf("poster", "cover", "folder").any { n.contains(it) }
        } ?: images.first()).url
    }

    fun clearCache() = cache.clear()

    private fun decodePath(path: String): String =
        runCatching { URLDecoder.decode(path.replace("+", "%2B"), "UTF-8") }.getOrDefault(path)

    private fun parseDate(rowText: String): Long {
        val match = dateRegex.find(rowText)?.value?.trim() ?: return 0L
        for (pattern in datePatterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = TimeZone.getDefault() }
                sdf.parse(match)?.let { return it.time }
            } catch (_: Exception) {
            }
        }
        return 0L
    }

    private fun parseSize(rowText: String): String {
        val m = sizeRegex.findAll(rowText).lastOrNull() ?: return ""
        return "${m.groupValues[1]} ${m.groupValues[2].uppercase()}B"
    }
}
