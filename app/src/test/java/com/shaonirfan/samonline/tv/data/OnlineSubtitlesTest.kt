package com.shaonirfan.samonline.tv.data

import com.shaonirfan.samonline.tv.data.remote.MovieHash
import com.shaonirfan.samonline.tv.data.remote.OpenSubtitlesClient
import com.shaonirfan.samonline.tv.data.remote.OpenSubtitlesException
import com.shaonirfan.samonline.tv.data.repository.SrtShift
import com.shaonirfan.samonline.tv.data.repository.SubtitleQuery
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Pure logic tests always run. The API tests run against tools/mock_opensubtitles.py:
 *   python3 tools/mock_opensubtitles.py 8766 &   then   MOCK_OS=http://127.0.0.1:8766 ./gradlew :app:testDebugUnitTest
 */
class OnlineSubtitlesTest {
    private val mock = System.getenv("MOCK_OS").orEmpty()
    private val ua = "SAMOnlineTV v1.0.4"

    @Test
    fun queryFromEpisodeFile() {
        val q = SubtitleQuery.from("http://x/TV/Breaking Bad/Season 1/Breaking.Bad.S01E05.720p.BluRay.mkv", "Breaking Bad (TV Series 2008–2013) 720p")
        assertEquals(SubtitleQuery("Breaking Bad", null, 1, 5), q)
    }

    @Test
    fun queryFromMovieFile() {
        val q = SubtitleQuery.from("http://x/(2023)/Oppenheimer (2023)/Oppenheimer.2023.1080p.WEB-DL.x265.mkv", "Oppenheimer")
        assertEquals(SubtitleQuery("Oppenheimer", 2023, null, null), q)
    }

    @Test
    fun queryFromBareEpisodeName() {
        val q = SubtitleQuery.from("http://x/Frieren/Season 1/S01E03.mkv", "Frieren (2023)")
        assertEquals("Frieren", q.title)
        assertEquals(3, q.episode)
    }

    @Test
    fun srtShift() {
        val srt = "1\n00:00:01,000 --> 00:00:03,500\nHi\n\n2\n01:00:00,250 --> 01:00:02,000\nBye\n"
        val later = SrtShift.shift(srt, 1500)
        assertTrue(later.contains("00:00:02,500 --> 00:00:05,000"))
        assertTrue(later.contains("01:00:01,750 --> 01:00:03,500"))
        assertTrue(SrtShift.shift(srt, -5000).contains("00:00:00,000 --> 00:00:00,000"))
    }

    @Test
    fun movieHashMatchesReferenceAlgorithm() {
        // Same data the mock server generates; the reference hash is computed by Python there.
        val f = File.createTempFile("vid", ".bin")
        f.writeBytes(ByteArray(300_000) { i -> ((i * 7 + 3) % 256).toByte() })
        val h = MovieHash.ofFile(f.absolutePath)
        assertEquals(16, h!!.length)
        assertEquals(referenceHash(f), h)
    }

    private fun referenceHash(f: File): String {
        val b = f.readBytes()
        var h = b.size.toLong()
        for (start in listOf(0, b.size - 65536)) {
            val bb = java.nio.ByteBuffer.wrap(b, start, 65536).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            repeat(65536 / 8) { h += bb.long }
        }
        return "%016x".format(h)
    }

    @Test
    fun apiFlowAgainstMock() = runBlocking {
        assumeTrue("MOCK_OS not set", mock.isNotBlank())
        val client = OpenSubtitlesClient("testkey123", ua, "$mock/api/v1")

        // Fingerprint over HTTP (2 Range requests) equals the server's own hash -> exact match.
        val hash = MovieHash.ofUrl("$mock/video.mkv", ua)
        val local = File.createTempFile("vid", ".bin").apply { writeBytes(ByteArray(300_000) { i -> ((i * 7 + 3) % 256).toByte() }) }
        assertEquals(MovieHash.ofFile(local.absolutePath), hash)

        val byHash = client.search(hash, null, null, null, null)
        assertEquals(1, byHash.size)
        assertTrue(byHash.single().hashMatch)

        // Title search with mixed-case query: client must lowercase + sort params (mock rejects otherwise).
        val byTitle = client.search(null, "The Movie", 2023, null, null)
        assertEquals(3, byTitle.size)

        // Download needs a login.
        val noLogin = runCatching { client.download(2) }.exceptionOrNull()
        assertTrue(noLogin is OpenSubtitlesException)

        assertTrue(runCatching { client.login("user", "wrong") }.exceptionOrNull()?.message!!.contains("Wrong"))
        assertEquals(20, client.login("user", "pass"))
        val (text, remaining) = client.download(2)
        assertEquals(19, remaining)
        assertTrue("windows-1252 decoded: $text", text.contains("Café is open"))

        val quota = runCatching { client.download(999) }.exceptionOrNull() as OpenSubtitlesException
        assertTrue(quota.quotaExceeded)
    }

    @Test
    fun noKeyMeansDisabled() = runBlocking {
        val client = OpenSubtitlesClient("", ua)
        assertFalse(client.isConfigured)
        val e = runCatching { client.search(null, "x", null, null, null) }.exceptionOrNull()
        assertTrue(e is OpenSubtitlesException)
        assertNull(client.token)
    }
}
