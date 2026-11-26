package com.shaonirfan.samonline.tv.data

import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.local.PosterStore
import com.shaonirfan.samonline.tv.data.repository.SearchRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** The saved search index must load fast after a reboot, and old (JSON) indexes are upgraded once. */
class SearchIndexStoreTest {

    private fun legacyJson(n: Int, builtAt: Long): String {
        val arr = JSONArray()
        arr.put(JSONArray().put("Friends (TV Series 1994–2004) 720p").put("http://s/TV/Friends/").put("TV & Web Series").put(0))
        arr.put(JSONArray().put("Friendsgiving (2020) 1080p").put("http://s/M/Friendsgiving/").put("English Movies").put(0))
        for (i in 0 until n) {
            arr.put(JSONArray().put("Some.Movie.$i.2019.1080p.WEB-DL.x265").put("http://s/M/$i/").put("English Movies").put(0))
        }
        return JSONObject().put("builtAt", builtAt).put("items", arr).toString()
    }

    @Test
    fun legacyIndexIsUpgradedThenLoadsFast() = runBlocking {
        val dir: File = Files.createTempDirectory("idx").toFile()
        val posters = PosterStore(JsonFileStore(dir, "posters.json"))
        val builtAt = System.currentTimeMillis() - 3_600_000
        JsonFileStore(dir, "search_index.json").write(legacyJson(32_000, builtAt))

        // First start after the update: reads the old JSON once and saves the fast format.
        val first = SearchRepository(JsonFileStore(dir, "search_index_v2.tsv"), posters, JsonFileStore(dir, "search_index.json"))
        val upgradeStart = System.nanoTime()
        val r1 = first.search("friends")
        val upgradeMs = (System.nanoTime() - upgradeStart) / 1_000_000
        assertEquals("Friends", r1.first().displayTitle)
        assertTrue(r1.first().isSeries)
        assertFalse("legacy file removed after upgrade", File(dir, "sam_data/search_index.json").exists() || File(dir, "search_index.json").exists())
        assertEquals(builtAt, first.state.value.builtAt) // keeps its age: no needless re-crawl

        // Every later start (e.g. after a reboot): plain load, no title re-cleaning.
        val second = SearchRepository(JsonFileStore(dir, "search_index_v2.tsv"), posters, JsonFileStore(dir, "search_index.json"))
        val loadStart = System.nanoTime()
        val r2 = second.search("friends")
        val loadMs = (System.nanoTime() - loadStart) / 1_000_000
        assertEquals(r1.map { it.url }, r2.map { it.url })
        assertEquals(32_002, second.state.value.count)
        assertTrue(second.state.value.ready)
        println("32k titles: legacy upgrade ${upgradeMs} ms, reload ${loadMs} ms")
        assertTrue("reload should be much faster than the upgrade ($loadMs ms vs $upgradeMs ms)", loadMs * 3 < upgradeMs)
    }
}
