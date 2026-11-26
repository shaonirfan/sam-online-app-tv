package com.shaonirfan.samonline.tv.player

import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackLabelsTest {
    private fun sub(label: String?, lang: String?, mime: String = MimeTypes.TEXT_SSA, flags: Int = 0, roles: Int = 0) =
        TrackInfo(label = label, language = lang, sampleMimeType = mime, selectionFlags = flags, roleFlags = roles)

    @Test
    fun siteStampIsIgnored() {
        assertEquals("English · ASS", TrackLabels.subtitle(sub("MoviesMod.org", "eng")))
        assertEquals("Japanese · SRT", TrackLabels.subtitle(sub("t.me/somechannel", "jpn", MimeTypes.APPLICATION_SUBRIP)))
        assertTrue(TrackLabels.isSpamName("www.example.com"))
        assertTrue(TrackLabels.isSpamName("Join @animechannel"))
        assertFalse(TrackLabels.isSpamName("Full Subtitles (Korean names)"))
        assertFalse(TrackLabels.isSpamName("Signs & Songs"))
    }

    @Test
    fun realTitleKept() {
        assertEquals(
            "English · Full Subtitles (Korean names) · ASS",
            TrackLabels.subtitle(sub("Full Subtitles (Korean names)", "eng")),
        )
        assertEquals("English · SRT · Forced", TrackLabels.subtitle(sub(null, "en", MimeTypes.APPLICATION_SUBRIP, flags = C.SELECTION_FLAG_FORCED)))
        assertEquals("English · SRT · SDH", TrackLabels.subtitle(sub("English", "eng", MimeTypes.APPLICATION_SUBRIP, roles = C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND)))
    }

    @Test
    fun cuesKeepOriginalFormat() {
        val f = TrackInfo(language = "eng", sampleMimeType = MimeTypes.APPLICATION_MEDIA3_CUES, codecs = MimeTypes.TEXT_SSA)
        assertEquals("English · ASS", TrackLabels.subtitle(f))
    }

    @Test
    fun languagesAndFallbacks() {
        assertEquals("Middle English", TrackLabels.language("enm"))
        assertEquals("Bangla", TrackLabels.language("ben"))
        assertEquals("English", TrackLabels.language("en"))
        assertEquals(null, TrackLabels.language("und"))
        assertEquals("Subtitle", TrackLabels.subtitle(sub("MoviesMod.org", "und", "application/x-unknown")))
    }

    @Test
    fun audioLabels() {
        val f = TrackInfo(label = "MoviesMod.org", language = "jpn", sampleMimeType = MimeTypes.AUDIO_E_AC3, channelCount = 6)
        assertEquals("Japanese · 5.1 · Dolby Digital+", TrackLabels.audio(f))
        val g = TrackInfo(language = "hin", sampleMimeType = MimeTypes.AUDIO_AAC, channelCount = 2)
        assertEquals("Hindi · Stereo · AAC", TrackLabels.audio(g))
    }

    @Test
    fun duplicatesNumbered() {
        assertEquals(
            listOf("English · ASS", "English · ASS (2)", "Japanese · ASS", "English · ASS (3)"),
            TrackLabels.disambiguate(listOf("English · ASS", "English · ASS", "Japanese · ASS", "English · ASS")),
        )
    }
}
