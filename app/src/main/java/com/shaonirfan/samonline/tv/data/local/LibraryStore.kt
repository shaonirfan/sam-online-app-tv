package com.shaonirfan.samonline.tv.data.local

import android.content.Context
import com.shaonirfan.samonline.tv.data.model.SavedTitle
import com.shaonirfan.samonline.tv.data.model.WatchProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** "My List" and per-URL watch progress (resume positions + Continue Watching). */
class LibraryStore(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listFile = JsonFileStore(context, "my_list.json")
    private val progressFile = JsonFileStore(context, "watch_progress.json")

    private val _myList = MutableStateFlow(loadList())
    val myList: StateFlow<List<SavedTitle>> = _myList.asStateFlow()

    private val _progress = MutableStateFlow(loadProgress())
    val progress: StateFlow<Map<String, WatchProgress>> = _progress.asStateFlow()

    // ---- My List ----
    fun isSaved(url: String) = _myList.value.any { it.url == url }

    fun toggle(title: SavedTitle): Boolean {
        var nowSaved = false
        _myList.update { list ->
            if (list.any { it.url == title.url }) {
                list.filterNot { it.url == title.url }
            } else {
                nowSaved = true
                listOf(title) + list
            }
        }
        persistList()
        return nowSaved
    }

    // ---- Progress ----
    fun progressFor(url: String): WatchProgress? = _progress.value[url]

    /** Most recent watch inside a series / movie folder, used for "Resume" on details. */
    fun lastWatchedUnder(folderUrl: String): WatchProgress? =
        _progress.value.values.filter { it.url.startsWith(folderUrl) }.maxByOrNull { it.updatedAt }

    fun saveProgress(p: WatchProgress) {
        _progress.update { it + (p.url to p) }
        persistProgress()
    }

    fun removeProgress(url: String) {
        _progress.update { it - url }
        persistProgress()
    }

    /** One entry per title folder: the most recently watched, still in-progress episode. */
    fun continueWatching(all: Map<String, WatchProgress>): List<WatchProgress> =
        all.values.filter { it.isInProgress }
            .sortedByDescending { it.updatedAt }
            .distinctBy { it.parentUrl.ifBlank { it.url } }
            .take(20)

    private fun loadList(): List<SavedTitle> = runCatching {
        val arr = JSONArray(listFile.read() ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            SavedTitle(
                url = o.getString("url"),
                title = o.optString("title"),
                poster = o.optString("poster").ifBlank { null },
                subtitle = o.optString("subtitle"),
                addedAt = o.optLong("addedAt"),
            )
        }
    }.getOrDefault(emptyList())

    private fun persistList() {
        val snapshot = _myList.value
        scope.launch {
            val arr = JSONArray()
            snapshot.forEach {
                arr.put(
                    JSONObject().put("url", it.url).put("title", it.title).put("poster", it.poster ?: "")
                        .put("subtitle", it.subtitle).put("addedAt", it.addedAt)
                )
            }
            listFile.write(arr.toString())
        }
    }

    private fun loadProgress(): Map<String, WatchProgress> = runCatching {
        val arr = JSONArray(progressFile.read() ?: return emptyMap())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            WatchProgress(
                url = o.getString("url"),
                title = o.optString("title"),
                seriesTitle = o.optString("seriesTitle"),
                parentUrl = o.optString("parentUrl"),
                poster = o.optString("poster").ifBlank { null },
                positionMs = o.optLong("pos"),
                durationMs = o.optLong("dur"),
                updatedAt = o.optLong("at"),
            )
        }.associateBy { it.url }
    }.getOrDefault(emptyMap())

    private fun persistProgress() {
        // Keep the file bounded: the 400 most recent entries.
        val snapshot = _progress.value.values.sortedByDescending { it.updatedAt }.take(400)
        scope.launch {
            val arr = JSONArray()
            snapshot.forEach {
                arr.put(
                    JSONObject().put("url", it.url).put("title", it.title).put("seriesTitle", it.seriesTitle)
                        .put("parentUrl", it.parentUrl).put("poster", it.poster ?: "")
                        .put("pos", it.positionMs).put("dur", it.durationMs).put("at", it.updatedAt)
                )
            }
            progressFile.write(arr.toString())
        }
    }
}
