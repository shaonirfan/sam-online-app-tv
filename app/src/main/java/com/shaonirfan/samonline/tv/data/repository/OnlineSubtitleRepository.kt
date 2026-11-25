package com.shaonirfan.samonline.tv.data.repository

import com.shaonirfan.samonline.tv.data.local.SettingsStore
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.remote.MovieHash
import com.shaonirfan.samonline.tv.data.remote.OnlineSubtitle
import com.shaonirfan.samonline.tv.data.remote.OpenSubtitlesClient
import com.shaonirfan.samonline.tv.data.remote.OpenSubtitlesException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** What we know about a video when searching for its subtitles. */
data class SubtitleQuery(
    val title: String,
    val year: Int?,
    val season: Int?,
    val episode: Int?,
) {
    companion object {
        private val seRegex = Regex("""(?i)\bs(\d{1,2})[ ._-]*e(\d{1,3})\b""")
        private val xRegex = Regex("""(?i)\b(\d{1,2})x(\d{2,3})\b""")

        /** Built from the file name, falling back to the series / movie folder title. */
        fun from(videoUrl: String, seriesTitle: String): SubtitleQuery {
            val file = NameCleaner.decode(videoUrl.substringAfterLast('/'))
            val se = seRegex.find(file) ?: xRegex.find(file)
            val season = se?.groupValues?.get(1)?.toIntOrNull()
            val episode = se?.groupValues?.get(2)?.toIntOrNull()
            val fromFile = NameCleaner.displayTitle(if (se != null) file.substring(0, se.range.first) else file)
                .trim(' ', '-', '.')
            val title = when {
                seriesTitle.isNotBlank() && (season != null || fromFile.length < 2) -> NameCleaner.displayTitle(seriesTitle)
                fromFile.isNotBlank() -> fromFile
                else -> NameCleaner.displayTitle(seriesTitle)
            }
            val year = (NameCleaner.year(file) ?: NameCleaner.year(seriesTitle))?.toIntOrNull()
            return SubtitleQuery(title, year.takeIf { season == null }, season, episode)
        }
    }
}

class OnlineSubtitleRepository(
    private val client: OpenSubtitlesClient,
    private val settings: SettingsStore,
    private val dir: File,
    private val userAgent: String,
) {
    private val loginMutex = Mutex()

    val isConfigured: Boolean get() = client.isConfigured

    /** A subtitle downloaded earlier for this video (attached automatically next time). */
    fun cached(videoUrl: String): File? = fileFor(videoUrl).takeIf { it.exists() && it.length() > 0 }

    private fun fileFor(videoUrl: String): File {
        val digest = MessageDigest.getInstance("SHA-1").digest(videoUrl.toByteArray())
        return File(dir, digest.joinToString("") { "%02x".format(it) }.take(24) + ".en.srt")
    }

    /**
     * Fingerprint search first (exact release, in sync), then title/episode search.
     * Exact matches first, then by popularity.
     */
    suspend fun search(videoUrl: String, seriesTitle: String): List<OnlineSubtitle> {
        val q = SubtitleQuery.from(videoUrl, seriesTitle)
        val hash = when {
            videoUrl.startsWith("http") -> MovieHash.ofUrl(videoUrl, userAgent)
            videoUrl.startsWith("/") -> MovieHash.ofFile(videoUrl)
            else -> null
        }
        val results = mutableListOf<OnlineSubtitle>()
        if (hash != null) {
            results += runCatching { client.search(hash, null, null, q.season, q.episode) }.getOrDefault(emptyList())
        }
        if (results.count { it.hashMatch } < 3) {
            results += client.search(null, q.title, q.year, q.season, q.episode)
        }
        return results
            .filter { it.fileId > 0 && it.language.equals("en", ignoreCase = true) }
            .distinctBy { it.fileId }
            .sortedWith(compareByDescending<OnlineSubtitle> { it.hashMatch }.thenBy { it.hearingImpaired }.thenByDescending { it.downloads })
            .take(25)
    }

    /** Downloads the subtitle, stores it for this video and returns the file + downloads left today. */
    suspend fun download(sub: OnlineSubtitle, videoUrl: String): Pair<File, Int> {
        ensureLoggedIn()
        val (text, remaining) = try {
            client.download(sub.fileId)
        } catch (e: OpenSubtitlesException) {
            // An expired token: log in again once and retry.
            if (!e.quotaExceeded && e.message?.contains("log in") == true && settings.openSubtitlesUser.isNotBlank()) {
                client.logout(); ensureLoggedIn(); client.download(sub.fileId)
            } else throw e
        }
        val file = withContext(Dispatchers.IO) {
            dir.mkdirs()
            fileFor(videoUrl).also { it.writeText(text) }
        }
        return file to remaining
    }

    private suspend fun ensureLoggedIn() = loginMutex.withLock {
        if (client.token != null) return@withLock
        val user = settings.openSubtitlesUser
        val pass = settings.openSubtitlesPassword
        if (user.isNotBlank() && pass.isNotBlank()) client.login(user, pass)
        // Without a login the API still allows a few anonymous downloads per day.
    }

    suspend fun testLogin(user: String, pass: String): Int {
        client.logout()
        return client.login(user, pass)
    }

    fun logout() = client.logout()

    /** Shifts every timestamp of an SRT file by [deltaMs] (subtitle sync), in place. */
    suspend fun shift(file: File, deltaMs: Long) = withContext(Dispatchers.IO) {
        file.writeText(SrtShift.shift(file.readText(), deltaMs))
    }
}

object SrtShift {
    private val time = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{3})""")

    fun shift(srt: String, deltaMs: Long): String = time.replace(srt) { m ->
        val (h, mi, s, ms) = m.destructured
        val total = (h.toLong() * 3_600_000 + mi.toLong() * 60_000 + s.toLong() * 1000 + ms.toLong() + deltaMs).coerceAtLeast(0)
        "%02d:%02d:%02d,%03d".format(total / 3_600_000, total / 60_000 % 60, total / 1000 % 60, total % 1000)
    }
}
