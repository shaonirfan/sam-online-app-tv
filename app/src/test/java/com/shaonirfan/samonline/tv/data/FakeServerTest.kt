package com.shaonirfan.samonline.tv.data

import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.local.PosterStore
import com.shaonirfan.samonline.tv.data.model.Category
import com.shaonirfan.samonline.tv.data.model.FolderContent
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.remote.DirectoryScraper
import com.shaonirfan.samonline.tv.data.remote.Servers
import com.shaonirfan.samonline.tv.data.remote.SubtitleFinder
import com.shaonirfan.samonline.tv.data.repository.CatalogRepository
import com.shaonirfan.samonline.tv.data.repository.SearchRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * Runs the scraper / catalog / search logic against a local fake DHAKA-FLIX tree.
 * Start it with: python3 tools/make_fakeflix.py /tmp/fakeflix && cd /tmp/fakeflix && python3 -m http.server 8765
 * then run with FAKEFLIX=http://127.0.0.1:8765 ./gradlew :app:testDebugUnitTest
 * (skipped when FAKEFLIX is not set, e.g. in CI).
 */
class FakeServerTest {
    private val base = System.getenv("FAKEFLIX").orEmpty()
    private lateinit var catalog: CatalogRepository
    private lateinit var posters: PosterStore
    private lateinit var dir: java.io.File

    @Before
    fun setUp() {
        assumeTrue("FAKEFLIX not set", base.isNotBlank())
        Servers.categories = listOf(
            Category("english", "English Movies", "$base/DHAKA-FLIX-7/English%20Movies/"),
            Category("series", "TV & Web Series", "$base/DHAKA-FLIX-12/TV-WEB-Series/", isSeries = true),
            Category("anime", "Anime & Cartoon", "$base/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/", isSeries = true),
        )
        DirectoryScraper.clearCache()
        dir = Files.createTempDirectory("sam").toFile()
        posters = PosterStore(JsonFileStore(dir, "posters.json"))
        catalog = CatalogRepository(posters, JsonFileStore(dir, "rows.json"))
    }

    @Test
    fun h5aiFallbackPageIsParsedWithoutParentOrAssetLinks() = runBlocking {
        val entries = DirectoryScraper.list(Servers.categories[0].url).getOrThrow()
        assertEquals(listOf("(2023) 1080p", "(2024) 1080p"), entries.map { it.name })
        assertTrue(entries.all { it.isFolder })
        assertTrue(entries.first { it.name.startsWith("(2024)") }.lastModifiedMs > 0)
    }

    @Test
    fun yearFolderIsAGridNotASeries() = runBlocking {
        val url = "$base/DHAKA-FLIX-7/English%20Movies/%282024%29%201080p/"
        val content = catalog.loadFolder(url, "(2024) 1080p", null).getOrThrow()
        assertTrue("year folder containing 'Kill Part 1' must stay a grid: $content", content is FolderContent.Grid)
        assertEquals(3, (content as FolderContent.Grid).items.size)
    }

    @Test
    fun movieFolderIsATitleWithPosterAndSubtitle() = runBlocking {
        val url = "$base/DHAKA-FLIX-7/English%20Movies/%282024%29%201080p/Dune%20Part%20Two%20%282024%29%201080p/"
        val content = catalog.loadFolder(url, "Dune Part Two (2024) 1080p", null).getOrThrow() as FolderContent.Title
        assertTrue(!content.isSeries)
        assertEquals(1, content.files.size)
        assertTrue(content.item.poster!!.endsWith("poster.jpg"))
        assertEquals("Dune Part Two", content.item.displayTitle)
        val subs = SubtitleFinder.find(content.files.first().url)
        assertEquals(1, subs.size)
        assertEquals("application/x-subrip", subs.first().mimeType)
    }

    @Test
    fun seriesWithSeasonFoldersAndDeepPoster() = runBlocking {
        val group = "$base/DHAKA-FLIX-12/TV-WEB-Series/TV%20Series%20%E2%98%85%20A%20%E2%80%94%20L/"
        val grid = catalog.loadFolder(group, "TV Series ★ A — L", null).getOrThrow()
        assertTrue("letter group must be a grid: $grid", grid is FolderContent.Grid)
        val bb = (grid as FolderContent.Grid).items.first { it.title.startsWith("Breaking") }
        val content = catalog.loadFolder(bb.url, bb.title, null).getOrThrow() as FolderContent.Title
        assertEquals(listOf("Season 1", "Season 2"), content.seasons.map { it.name })
        assertTrue(content.isSeries)
        val eps = catalog.episodes(content.seasons.first()).getOrThrow()
        assertEquals(10, eps.size)
        assertEquals("S01 · E10", eps.last().title)
        assertNotNull(catalog.posterFor(bb.url))
    }

    @Test
    fun oddlyNamedSeasonFolderAndSubsSubfolder() = runBlocking {
        val dark = "$base/DHAKA-FLIX-12/TV-WEB-Series/TV%20Series%20%E2%98%85%20A%20%E2%80%94%20L/Dark%20%282017%29/"
        val content = catalog.loadFolder(dark, "Dark (2017)", null).getOrThrow() as FolderContent.Title
        assertEquals(1, content.seasons.size)
        val eps = catalog.episodes(content.seasons.first()).getOrThrow()
        val subs = SubtitleFinder.find(eps.first().url)
        assertEquals("English", subs.single().label)
        assertTrue(SubtitleFinder.find(eps[1].url).isEmpty())
    }

    @Test
    fun flatEpisodesAreNaturallySorted() = runBlocking {
        val url = "$base/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/Frieren%20%282023%29/"
        val content = catalog.loadFolder(url, "Frieren (2023)", null).getOrThrow() as FolderContent.Title
        assertTrue(content.isSeries)
        assertEquals(listOf("Episode 1", "Episode 2", "Episode 3", "Episode 10", "Episode 11"), content.files.map { it.title })
    }

    @Test
    fun categoryRowsFindTitlesAcrossNestingLevels() = runBlocking {
        val movies = catalog.categoryRow(Servers.categories[0])
        assertEquals(setOf("Dune Part Two", "Inside Out 2", "Kill Part 1", "Oppenheimer"), movies.map { it.displayTitle }.toSet())
        val series = catalog.categoryRow(Servers.categories[1])
        assertEquals(setOf("Breaking Bad", "Dark"), series.map { it.displayTitle }.toSet())
    }

    @Test
    fun searchIndexBuildsAndMatches() = runBlocking {
        val search = SearchRepository(JsonFileStore(dir, "index.json"), posters)
        search.rebuild()
        for (i in 0 until 100) { if (!search.state.value.building && search.state.value.ready) break; delay(100) }
        assertTrue(search.state.value.count > 0)
        assertEquals("Dune Part Two", search.search("dune").first().displayTitle)
        assertTrue(search.search("breaking").any { it.displayTitle == "Breaking Bad" })
        assertTrue(search.search("part two").isNotEmpty())
    }

    @Test
    fun nameCleaning() {
        assertEquals("Dune Part Two", NameCleaner.displayTitle("Dune.Part.Two.2024.1080p.WEB-DL.x265.mkv"))
        assertEquals("Breaking Bad", NameCleaner.displayTitle("Breaking Bad (TV Series 2008–2013) 720p"))
        assertEquals("2024", NameCleaner.year("Dune Part Two (2024) 1080p"))
        assertEquals("S02 · E05", NameCleaner.episodeLabel("Show.S02E05.mkv", 1))
    }
}
