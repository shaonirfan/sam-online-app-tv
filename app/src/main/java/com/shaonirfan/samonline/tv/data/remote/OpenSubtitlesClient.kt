package com.shaonirfan.samonline.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineSubtitle(
    val fileId: Long,
    val release: String,
    val language: String,
    val downloads: Int,
    /** Matched by file fingerprint: almost always perfectly in sync. */
    val hashMatch: Boolean,
    val hearingImpaired: Boolean,
)

class OpenSubtitlesException(message: String, val quotaExceeded: Boolean = false) : IOException(message)

/**
 * Minimal OpenSubtitles.com REST client (api.opensubtitles.com/api/v1):
 * login -> JWT, search by moviehash and/or title, download -> temporary link -> .srt text.
 */
class OpenSubtitlesClient(
    private val apiKey: String,
    private val userAgent: String,
    private var baseUrl: String = "https://api.opensubtitles.com/api/v1",
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()

    @Volatile var token: String? = null
        private set

    /** Logs in; returns the daily download allowance reported by the server. */
    suspend fun login(username: String, password: String): Int = withContext(Dispatchers.IO) {
        val body = JSONObject().put("username", username).put("password", password)
        val res = request("POST", "/login", body, auth = false)
        token = res.optString("token").ifBlank { throw OpenSubtitlesException("Login failed") }
        // VIP accounts are sent to a different host.
        res.optString("base_url").takeIf { it.isNotBlank() && !it.contains("api.opensubtitles.com/api") }?.let { host ->
            if (!baseUrl.startsWith("http://127.") && !baseUrl.startsWith("http://localhost")) {
                baseUrl = "https://$host/api/v1"
            }
        }
        res.optJSONObject("user")?.optInt("allowed_downloads", 20) ?: 20
    }

    fun logout() {
        token = null
    }

    suspend fun search(
        moviehash: String?,
        query: String?,
        year: Int?,
        season: Int?,
        episode: Int?,
        language: String = "en",
    ): List<OnlineSubtitle> = withContext(Dispatchers.IO) {
        // The API wants lowercase, alphabetically sorted parameters (otherwise it redirects).
        val params = sortedMapOf<String, String>()
        params["languages"] = language
        moviehash?.let { params["moviehash"] = it }
        query?.takeIf { it.isNotBlank() }?.let { params["query"] = it.lowercase() }
        year?.let { params["year"] = it.toString() }
        season?.let { params["season_number"] = it.toString() }
        episode?.let { params["episode_number"] = it.toString() }
        if (season != null || episode != null) params["type"] = "episode"
        val qs = params.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8").replace("+", "%20")}" }
        val res = request("GET", "/subtitles?$qs", null, auth = false)
        val data = res.optJSONArray("data") ?: return@withContext emptyList()
        (0 until data.length()).mapNotNull { i ->
            val a = data.optJSONObject(i)?.optJSONObject("attributes") ?: return@mapNotNull null
            val file = a.optJSONArray("files")?.optJSONObject(0) ?: return@mapNotNull null
            OnlineSubtitle(
                fileId = file.optLong("file_id"),
                release = a.optString("release").ifBlank { file.optString("file_name") },
                language = a.optString("language"),
                downloads = a.optInt("download_count"),
                hashMatch = a.optBoolean("moviehash_match"),
                hearingImpaired = a.optBoolean("hearing_impaired"),
            )
        }
    }

    /** Returns the subtitle text (SRT) and how many downloads are left today (-1 if unknown). */
    suspend fun download(fileId: Long): Pair<String, Int> = withContext(Dispatchers.IO) {
        val res = request("POST", "/download", JSONObject().put("file_id", fileId).put("sub_format", "srt"), auth = true)
        val link = res.optString("link").ifBlank { throw OpenSubtitlesException(res.optString("message", "No download link")) }
        val conn = (URL(link).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (conn.responseCode !in 200..299) throw OpenSubtitlesException("Subtitle file HTTP ${conn.responseCode}")
            val bytes = conn.inputStream.use { it.readBytes() }
            decode(bytes) to res.optInt("remaining", -1)
        } finally {
            conn.disconnect()
        }
    }

    private fun request(method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject {
        if (!isConfigured) throw OpenSubtitlesException("Online subtitles aren't set up in this build (no API key)")
        val conn = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            instanceFollowRedirects = true
            setRequestProperty("Api-Key", apiKey)
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
            if (auth) token?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            body?.let { b -> conn.outputStream.use { it.write(b.toString().toByteArray()) } }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
            val json = runCatching { JSONObject(text) }.getOrElse { JSONObject() }
            when {
                code in 200..299 -> return json
                code == 406 || code == 429 && path.startsWith("/download") -> throw OpenSubtitlesException(
                    json.optString("message", "Daily subtitle download limit reached"), quotaExceeded = true,
                )
                code == 401 -> throw OpenSubtitlesException(
                    if (path == "/login") "Wrong OpenSubtitles username or password"
                    else "OpenSubtitles needs you to log in (Settings → Subtitles account)",
                )
                code == 429 -> throw OpenSubtitlesException("OpenSubtitles is busy - try again in a few seconds")
                else -> throw OpenSubtitlesException(json.optString("message").ifBlank { "OpenSubtitles error $code" })
            }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** UTF-8 if it decodes cleanly, otherwise Windows-1252 (many old SRTs). */
        fun decode(bytes: ByteArray): String {
            val utf8 = bytes.decodeToString()
            val text = if (utf8.contains('�')) String(bytes, charset("windows-1252")) else utf8
            return text.removePrefix("﻿")
        }
    }
}

/**
 * OpenSubtitles "moviehash": file size + the sum of the first and last 64 KB read as
 * little-endian 64-bit words. Lets the server find subtitles made for this exact release.
 * Over HTTP it needs 2 small Range requests, not the whole file.
 */
object MovieHash {
    private const val CHUNK = 65536

    fun of(size: Long, head: ByteArray, tail: ByteArray): String {
        var hash = size
        hash += sum(head)
        hash += sum(tail)
        return "%016x".format(hash)
    }

    private fun sum(bytes: ByteArray): Long {
        var s = 0L
        val n = bytes.size / 8 * 8
        var i = 0
        while (i < n) {
            var v = 0L
            for (b in 7 downTo 0) v = (v shl 8) or (bytes[i + b].toLong() and 0xff)
            s += v
            i += 8
        }
        return s
    }

    fun ofFile(path: String): String? = runCatching {
        RandomAccessFile(path, "r").use { f ->
            val size = f.length()
            if (size < CHUNK) return null
            val head = ByteArray(CHUNK).also { f.seek(0); f.readFully(it) }
            val tail = ByteArray(CHUNK).also { f.seek(size - CHUNK); f.readFully(it) }
            of(size, head, tail)
        }
    }.getOrNull()

    suspend fun ofUrl(url: String, userAgent: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val size = (URL(url).openConnection() as HttpURLConnection).run {
                requestMethod = "HEAD"; connectTimeout = 8_000; readTimeout = 8_000
                setRequestProperty("User-Agent", userAgent)
                try { contentLengthLong } finally { disconnect() }
            }
            if (size < CHUNK * 2) return@runCatching null
            val head = range(url, 0, CHUNK - 1L, userAgent) ?: return@runCatching null
            val tail = range(url, size - CHUNK, size - 1, userAgent) ?: return@runCatching null
            of(size, head, tail)
        }.getOrNull()
    }

    private fun range(url: String, from: Long, to: Long, userAgent: String): ByteArray? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000; readTimeout = 10_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Range", "bytes=$from-$to")
        }
        return try {
            if (conn.responseCode != HttpURLConnection.HTTP_PARTIAL) return null
            val out = ByteArray((to - from + 1).toInt())
            conn.inputStream.use { input ->
                var off = 0
                while (off < out.size) {
                    val n = input.read(out, off, out.size - off)
                    if (n < 0) break
                    off += n
                }
                if (off != out.size) return null
            }
            out
        } finally {
            conn.disconnect()
        }
    }
}
