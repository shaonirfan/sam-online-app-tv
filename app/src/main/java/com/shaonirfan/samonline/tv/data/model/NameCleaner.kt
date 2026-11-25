package com.shaonirfan.samonline.tv.data.model

import java.net.URLDecoder

/** Turns raw folder/file names like "Dune.Part.Two.2024.1080p.WEB-DL" into readable titles. */
object NameCleaner {
    private val yearRegex = Regex("""(?<!\d)(19[3-9]\d|20[0-4]\d)(?!\d)""")
    private val qualityTokens = Regex(
        """(?i)\b(2160p|1080p|720p|480p|4k|uhd|hdr|hdr10|web[- ]?dl|webrip|bluray|blu[- ]?ray|brrip|bdrip|dvdrip|hdrip|hdtv|x264|x265|h\.?264|h\.?265|hevc|10bit|aac\d?(\.\d)?|ddp?\d?(\.\d)?|atmos|dts|esub|esubs|dual[- ]audio|amzn|dsnp|hmax|yts|yify|rarbg|psa|galaxyrg|tigole|qxr|pahe)\b.*$"""
    )

    fun decode(raw: String): String = try {
        URLDecoder.decode(raw.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        raw
    }

    fun year(raw: String): String? = yearRegex.findAll(decode(raw)).lastOrNull()?.value

    fun displayTitle(raw: String): String {
        var s = decode(raw).trim().trimEnd('/')
        if (looksLikeFile(s)) s = s.substringBeforeLast('.')
        // IMDb Top-250 folders are numbered: "089. Oppenheimer (2023)".
        s = s.replace(Regex("""^\s*\d{1,3}\s*[.)\-]\s+"""), "")
        s = s.replace('_', ' ').replace(Regex("""(?<=\w)\.(?=\w)"""), " ")
        s = qualityTokens.replace(s, "")
        // Drop the year and anything after it ("Movie Name 2023 1080p" -> "Movie Name"),
        // but only if there is a title before it.
        val y = yearRegex.find(s)
        if (y != null && y.range.first > 2) s = s.substring(0, y.range.first)
        s = s.replace(Regex("""[(\[{]\s*[)\]}]"""), "")
            .replace(Regex("""\s*[(\[{][^)\]}]*$"""), "") // unbalanced "(TV Series" left after the cut
            .replace(Regex("""\s+"""), " ")
            .trim(' ', '-', '(', '[', '.', ',')
        return s.ifBlank { decode(raw).trim().trimEnd('/') }
    }

    fun looksLikeFile(raw: String): Boolean {
        val ext = raw.substringAfterLast('.', "").lowercase()
        return ext.length in 2..4 && ext.all { it.isLetterOrDigit() } &&
            ext in setOf("mp4", "mkv", "avi", "mov", "webm", "m4v", "ts", "srt", "vtt", "jpg", "png", "jpeg", "webp")
    }

    /** "S01E05" / "Episode 5" / "E05" -> label for episode lists. */
    fun episodeLabel(raw: String, fallbackIndex: Int): String {
        val name = decode(raw)
        Regex("""(?i)s(\d{1,2})\s*e(\d{1,3})""").find(name)?.let {
            return "S${it.groupValues[1].padStart(2, '0')} · E${it.groupValues[2].padStart(2, '0')}"
        }
        Regex("""(?i)\b(?:ep(?:isode)?|e)\s*\.?\s*(\d{1,3})\b""").find(name)?.let {
            return "Episode ${it.groupValues[1].toInt()}"
        }
        return "Episode $fallbackIndex"
    }

    fun normalizeForSearch(input: String): String =
        decode(input).lowercase()
            .replace(Regex("""[._\-()+\[\]{}:,'!&]+"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
}
