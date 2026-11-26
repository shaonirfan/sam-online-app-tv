package com.shaonirfan.samonline.tv.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import java.util.Locale

/**
 * Human-readable names for audio and subtitle tracks, like a desktop player shows them:
 * "English · Full Subtitles (Korean names) · ASS". Releases often stamp every track's name
 * with the uploader's site ("MoviesMod.org"), so names that look like a site, URL or
 * channel are ignored and the language, format and flags are used instead.
 */
/** The track fields the labels are built from (plain data, so it's testable off-device). */
data class TrackInfo(
    val label: String? = null,
    val language: String? = null,
    val sampleMimeType: String? = null,
    val codecs: String? = null,
    val selectionFlags: Int = 0,
    val roleFlags: Int = 0,
    val channelCount: Int = Format.NO_VALUE,
) {
    companion object {
        fun of(f: Format) = TrackInfo(f.label, f.language, f.sampleMimeType, f.codecs, f.selectionFlags, f.roleFlags, f.channelCount)
    }
}

object TrackLabels {

    private val spamName = Regex(
        """(?i)(https?://|www\.|t\.me/|telegram|@[a-z0-9_]{3,}|\b[a-z0-9-]{2,}\.(org|com|net|in|me|xyz|io|cc|to|tv|link|site|online|pro|club|top|lol|vip|bd|co|info|biz|mx|ws|fun|lat|live|cam|world)\b)"""
    )

    /** ISO 639-2 codes the JVM doesn't always name ("eng" -> "English"). */
    private val iso3 = mapOf(
        "eng" to "English", "enm" to "Middle English", "jpn" to "Japanese", "kor" to "Korean", "hin" to "Hindi",
        "ben" to "Bangla", "ara" to "Arabic", "spa" to "Spanish", "fre" to "French", "fra" to "French",
        "ger" to "German", "deu" to "German", "chi" to "Chinese", "zho" to "Chinese", "por" to "Portuguese",
        "ita" to "Italian", "rus" to "Russian", "tha" to "Thai", "ind" to "Indonesian", "may" to "Malay",
        "msa" to "Malay", "tur" to "Turkish", "vie" to "Vietnamese", "urd" to "Urdu", "tam" to "Tamil",
        "tel" to "Telugu", "mal" to "Malayalam", "kan" to "Kannada", "mar" to "Marathi", "pol" to "Polish",
        "dut" to "Dutch", "nld" to "Dutch", "swe" to "Swedish", "nor" to "Norwegian", "dan" to "Danish",
        "fin" to "Finnish", "gre" to "Greek", "ell" to "Greek", "heb" to "Hebrew", "per" to "Persian",
        "fas" to "Persian", "ukr" to "Ukrainian", "cze" to "Czech", "ces" to "Czech", "hun" to "Hungarian",
        "rum" to "Romanian", "ron" to "Romanian", "fil" to "Filipino", "tgl" to "Tagalog",
    )

    fun isSpamName(name: String) = spamName.containsMatchIn(name)

    fun language(code: String?): String? {
        val c = code?.trim()?.lowercase()?.takeIf { it.isNotBlank() && it != "und" && it != "zxx" && it != "mul" } ?: return null
        iso3[c]?.let { return it }
        val base = c.substringBefore('-').substringBefore('_')
        iso3[base]?.let { return it }
        val name = Locale.forLanguageTag(c).getDisplayName(Locale.ENGLISH)
        return name.takeIf { it.isNotBlank() && !it.equals(c, ignoreCase = true) } ?: c
    }

    /** The original format, also for tracks Media3 already turned into cues. */
    private fun sourceMime(f: TrackInfo): String? {
        val m = f.sampleMimeType
        return if (m == MimeTypes.APPLICATION_MEDIA3_CUES) f.codecs ?: m else m
    }

    fun subtitleFormat(f: TrackInfo): String? = when (sourceMime(f)) {
        MimeTypes.TEXT_SSA -> "ASS"
        MimeTypes.APPLICATION_SUBRIP -> "SRT"
        MimeTypes.TEXT_VTT, MimeTypes.APPLICATION_MP4VTT -> "VTT"
        MimeTypes.APPLICATION_PGS -> "PGS"
        MimeTypes.APPLICATION_VOBSUB -> "VobSub"
        MimeTypes.APPLICATION_DVBSUBS -> "DVB"
        MimeTypes.APPLICATION_TTML -> "TTML"
        MimeTypes.APPLICATION_TX3G -> "TX3G"
        MimeTypes.APPLICATION_CEA608, MimeTypes.APPLICATION_CEA708 -> "CC"
        else -> null
    }

    fun audioCodec(f: TrackInfo): String? = when (f.sampleMimeType) {
        MimeTypes.AUDIO_AAC -> "AAC"
        MimeTypes.AUDIO_AC3 -> "Dolby Digital"
        MimeTypes.AUDIO_E_AC3, MimeTypes.AUDIO_E_AC3_JOC -> "Dolby Digital+"
        MimeTypes.AUDIO_TRUEHD -> "TrueHD"
        MimeTypes.AUDIO_DTS, MimeTypes.AUDIO_DTS_EXPRESS -> "DTS"
        MimeTypes.AUDIO_DTS_HD -> "DTS-HD"
        MimeTypes.AUDIO_OPUS -> "Opus"
        MimeTypes.AUDIO_VORBIS -> "Vorbis"
        MimeTypes.AUDIO_FLAC -> "FLAC"
        MimeTypes.AUDIO_MPEG, MimeTypes.AUDIO_MPEG_L2 -> "MP3"
        else -> null
    }

    private fun channels(n: Int): String? = when (n) {
        1 -> "Mono"; 2 -> "Stereo"; 6 -> "5.1"; 8 -> "7.1"; Format.NO_VALUE -> null; else -> "$n ch"
    }

    /** A track's own title, unless it is a site / channel stamp or just repeats the language. */
    private fun usefulName(f: TrackInfo, lang: String?): String? {
        val name = f.label?.trim()?.takeIf { it.isNotBlank() } ?: return null
        if (isSpamName(name)) return null
        if (lang != null && name.equals(lang, ignoreCase = true)) return null
        return name
    }

    fun subtitle(f: Format): String = subtitle(TrackInfo.of(f))

    fun subtitle(f: TrackInfo): String {
        val lang = language(f.language)
        val parts = listOfNotNull(
            lang,
            usefulName(f, lang),
            subtitleFormat(f),
            "Forced".takeIf { f.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
            "SDH".takeIf { f.roleFlags and (C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND or C.ROLE_FLAG_CAPTION) != 0 },
        )
        return parts.joinToString(" · ").ifBlank { "Subtitle" }
    }

    fun audio(f: Format): String = audio(TrackInfo.of(f))

    fun audio(f: TrackInfo): String {
        val lang = language(f.language)
        val parts = listOfNotNull(
            lang,
            usefulName(f, lang),
            channels(f.channelCount),
            audioCodec(f),
            "Commentary".takeIf { f.roleFlags and C.ROLE_FLAG_COMMENTARY != 0 },
        )
        return parts.joinToString(" · ").ifBlank { "Audio" }
    }

    /** Numbers repeated labels: "English · ASS", "English · ASS (2)". */
    fun disambiguate(labels: List<String>): List<String> {
        val total = labels.groupingBy { it }.eachCount()
        val seen = HashMap<String, Int>()
        return labels.map { l ->
            if ((total[l] ?: 0) < 2) l else {
                val n = (seen[l] ?: 0) + 1
                seen[l] = n
                if (n == 1) l else "$l ($n)"
            }
        }
    }
}
