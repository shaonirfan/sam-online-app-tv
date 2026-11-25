package com.shaonirfan.samonline.tv.data.local

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent folder-URL -> poster-URL map (BeeGear's "folder_posters.json" idea), so a
 * poster is discovered once and every later screen shows it instantly.
 * Misses are remembered too (for 3 days) to avoid re-crawling poster-less folders.
 */
@OptIn(FlowPreview::class)
class PosterStore(private val file: JsonFileStore) {
    constructor(context: Context) : this(JsonFileStore(context, "folder_posters.json"))

    private val posters = ConcurrentHashMap<String, String>()
    private val misses = ConcurrentHashMap<String, Long>()
    private val dirty = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        runCatching {
            val o = JSONObject(file.read() ?: "{}")
            o.optJSONObject("posters")?.let { p -> p.keys().forEach { k -> posters[k] = p.getString(k) } }
            o.optJSONObject("misses")?.let { m -> m.keys().forEach { k -> misses[k] = m.getLong(k) } }
        }
        dirty.debounce(1500).onEach { persist() }.launchIn(scope)
    }

    fun get(folderUrl: String): String? = posters[folderUrl]

    fun isKnownMiss(folderUrl: String): Boolean {
        val at = misses[folderUrl] ?: return false
        return System.currentTimeMillis() - at < 3L * 24 * 60 * 60 * 1000
    }

    fun put(folderUrl: String, poster: String?) {
        if (poster.isNullOrBlank()) {
            misses[folderUrl] = System.currentTimeMillis()
        } else {
            if (posters[folderUrl] == poster) return
            posters[folderUrl] = poster
            misses.remove(folderUrl)
        }
        dirty.tryEmit(Unit)
    }

    fun clear() {
        posters.clear()
        misses.clear()
        file.delete()
    }

    val size: Int get() = posters.size

    private fun persist() {
        val p = JSONObject()
        posters.forEach { (k, v) -> p.put(k, v) }
        val m = JSONObject()
        misses.forEach { (k, v) -> m.put(k, v) }
        file.write(JSONObject().put("posters", p).put("misses", m).toString())
    }
}
