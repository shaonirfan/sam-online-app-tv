package com.shaonirfan.samonline.tv.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.model.NameCleaner
import com.shaonirfan.samonline.tv.data.remote.Servers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.SocketException
import java.net.URL

enum class DownloadStatus { QUEUED, DOWNLOADING, PAUSED, COMPLETED, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val fileName: String,
    val url: String,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = -1L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val error: String = "",
    /** content:// or file path of the finished file. */
    val localUri: String = "",
) {
    val progress: Float get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
}

sealed interface EnqueueResult {
    data class Added(val fileName: String) : EnqueueResult
    data object AlreadyDone : EnqueueResult
    data object AlreadyQueued : EnqueueResult
    data object Resumed : EnqueueResult
}

/**
 * Single-active download queue (BeeGear's design): one file at a time, HTTP Range
 * resume of the .part file, auto-pause on connection drops and auto-resume when the
 * app comes back. Finished files go to Movies/SAM Online TV.
 */
class DownloadManager(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val stateFile = JsonFileStore(context, "download_queue.json")
    private val tempDir = File(context.filesDir, "dl_temp").apply { mkdirs() }

    private val _tasks = MutableStateFlow(load())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private var activeJob: Job? = null
    @Volatile private var activeId: String? = null

    fun enqueue(url: String, title: String): EnqueueResult {
        _tasks.value.firstOrNull { it.url == url }?.let { existing ->
            return when (existing.status) {
                DownloadStatus.COMPLETED -> EnqueueResult.AlreadyDone
                DownloadStatus.PAUSED, DownloadStatus.FAILED -> {
                    resume(existing.id); EnqueueResult.Resumed
                }
                else -> EnqueueResult.AlreadyQueued
            }
        }
        val fileName = sanitize(url.substringAfterLast('/').ifBlank { title })
        val task = DownloadTask(
            id = "${System.currentTimeMillis()}_${url.hashCode()}",
            title = NameCleaner.displayTitle(title),
            fileName = fileName,
            url = url,
        )
        _tasks.update { it + task }
        persist()
        startNextIfIdle()
        return EnqueueResult.Added(fileName)
    }

    fun pause(id: String) {
        if (activeId == id) {
            activeJob?.cancel()
            activeJob = null
            activeId = null
        }
        updateTask(id) {
            if (it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED) {
                it.copy(status = DownloadStatus.PAUSED, error = "")
            } else it
        }
        persist()
        startNextIfIdle()
    }

    fun resume(id: String) {
        val currentActive = activeId
        if (currentActive != null && currentActive != id) {
            activeJob?.cancel()
            activeJob = null
            activeId = null
            updateTask(currentActive) { it.copy(status = DownloadStatus.PAUSED) }
        }
        updateTask(id) { if (it.status != DownloadStatus.COMPLETED) it.copy(status = DownloadStatus.QUEUED, error = "") else it }
        persist()
        run(id)
    }

    fun delete(id: String) {
        val task = _tasks.value.firstOrNull { it.id == id } ?: return
        if (activeId == id) {
            activeJob?.cancel()
            activeJob = null
            activeId = null
        }
        runCatching {
            File(tempDir, "${task.fileName}.part").delete()
            if (task.localUri.startsWith("content://")) {
                context.contentResolver.delete(android.net.Uri.parse(task.localUri), null, null)
            } else if (task.localUri.isNotBlank()) {
                File(task.localUri).delete()
            }
        }
        _tasks.update { list -> list.filterNot { it.id == id } }
        persist()
        startNextIfIdle()
    }

    /** Called from MainActivity.onResume: picks up queued or connection-aborted tasks. */
    fun onAppResumed() {
        if (activeJob?.isActive == true) return
        val candidate = _tasks.value.firstOrNull {
            it.status == DownloadStatus.QUEUED ||
                (it.status == DownloadStatus.PAUSED && it.error.contains("abort", ignoreCase = true))
        } ?: return
        resume(candidate.id)
    }

    private fun startNextIfIdle() {
        if (activeJob?.isActive == true) return
        val next = _tasks.value.firstOrNull { it.status == DownloadStatus.QUEUED } ?: return
        run(next.id)
    }

    private fun run(id: String) {
        if (activeJob?.isActive == true) return
        val snapshot = _tasks.value.firstOrNull { it.id == id } ?: return
        activeId = id
        updateTask(id) { it.copy(status = DownloadStatus.DOWNLOADING, error = "") }
        persist()

        activeJob = scope.launch {
            val part = File(tempDir, "${snapshot.fileName}.part")
            var conn: HttpURLConnection? = null
            var aborted = false
            try {
                var existing = if (part.exists()) part.length() else 0L
                conn = (URL(snapshot.url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 25_000
                    setRequestProperty("User-Agent", Servers.USER_AGENT)
                    if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
                }
                conn.connect()
                val code = conn.responseCode
                val append = code == HttpURLConnection.HTTP_PARTIAL
                if (!append && existing > 0L) {
                    existing = 0L
                    part.delete()
                }
                if (code !in 200..299) throw IllegalStateException("Server HTTP $code")

                val length = conn.contentLengthLong
                val total = if (length > 0L) existing + length else -1L
                updateTask(id) { it.copy(downloadedBytes = existing, totalBytes = total) }

                var done = existing
                var lastUi = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(part, append).use { out ->
                        val buf = ByteArray(64 * 1024)
                        while (isActive) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (now - lastUi >= 500L) {
                                lastUi = now
                                updateTask(id) { it.copy(downloadedBytes = done) }
                            }
                        }
                    }
                }
                if (!isActive) return@launch

                val uri = moveToMovies(part, snapshot.fileName)
                updateTask(id) {
                    it.copy(
                        downloadedBytes = done, totalBytes = if (total > 0) total else done,
                        status = DownloadStatus.COMPLETED, error = "", localUri = uri,
                    )
                }
                persist()
            } catch (e: Exception) {
                val msg = e.localizedMessage ?: "Download interrupted"
                val connectionDrop = e is SocketException || listOf("abort", "broken pipe", "reset by peer", "timed out")
                    .any { msg.contains(it, ignoreCase = true) }
                val saved = if (part.exists()) part.length() else 0L
                aborted = connectionDrop || !isActive
                updateTask(id) {
                    it.copy(
                        downloadedBytes = maxOf(saved, it.downloadedBytes),
                        status = if (aborted) DownloadStatus.PAUSED else DownloadStatus.FAILED,
                        error = if (aborted) "Connection aborted (auto-resume ready)" else msg,
                    )
                }
                persist()
            } finally {
                runCatching { conn?.disconnect() }
                if (activeId == id) {
                    activeId = null
                    activeJob = null
                    if (!aborted) startNextIfIdle()
                }
            }
        }
    }

    private fun moveToMovies(part: File, fileName: String): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeFor(fileName))
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/SAM Online TV")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { out -> part.inputStream().use { it.copyTo(out, 64 * 1024) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                part.delete()
                return uri.toString()
            }
        }
        @Suppress("DEPRECATION")
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "SAM Online TV")
        val dest = runCatching { dir.mkdirs(); File(dir, fileName) }.getOrNull()
            ?.takeIf { dir.canWrite() }
            ?: File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir, fileName)
        if (dest.exists()) dest.delete()
        if (!part.renameTo(dest)) {
            part.inputStream().use { input -> FileOutputStream(dest).use { input.copyTo(it, 64 * 1024) } }
            part.delete()
        }
        MediaScannerConnection.scanFile(context, arrayOf(dest.absolutePath), null, null)
        return dest.absolutePath
    }

    private fun updateTask(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.update { list -> list.map { if (it.id == id) transform(it) else it } }
    }

    private fun sanitize(raw: String): String =
        NameCleaner.decode(raw).replace(Regex("""[\\/:*?"<>|]+"""), "_").trim().ifBlank { "sam_video.mp4" }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".mkv", true) -> "video/x-matroska"
        name.endsWith(".webm", true) -> "video/webm"
        name.endsWith(".avi", true) -> "video/x-msvideo"
        name.endsWith(".mov", true) -> "video/quicktime"
        else -> "video/mp4"
    }

    private fun load(): List<DownloadTask> = runCatching {
        val arr = JSONArray(stateFile.read() ?: return emptyList())
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val status = runCatching { DownloadStatus.valueOf(o.optString("status")) }.getOrDefault(DownloadStatus.PAUSED)
            DownloadTask(
                id = o.optString("id"), title = o.optString("title"), fileName = o.optString("fileName"),
                url = o.optString("url"), downloadedBytes = o.optLong("done"), totalBytes = o.optLong("total", -1L),
                // A task that was running when the app died resumes as "aborted".
                status = if (status == DownloadStatus.DOWNLOADING) DownloadStatus.PAUSED else status,
                error = if (status == DownloadStatus.DOWNLOADING) "Connection aborted (auto-resume ready)" else o.optString("error"),
                localUri = o.optString("localUri"),
            )
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val snapshot = _tasks.value
        val arr = JSONArray()
        snapshot.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("fileName", it.fileName).put("url", it.url)
                    .put("done", it.downloadedBytes).put("total", it.totalBytes).put("status", it.status.name)
                    .put("error", it.error).put("localUri", it.localUri)
            )
        }
        stateFile.write(arr.toString())
    }
}
