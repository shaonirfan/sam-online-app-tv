package com.shaonirfan.samonline.tv.data.local

import android.content.Context
import java.io.File

/** Tiny atomic JSON-file persistence used by the local stores (no DB needed). */
class JsonFileStore(dir: File, name: String) {
    constructor(context: Context, name: String) : this(File(context.filesDir, "sam_data"), name)

    init {
        dir.mkdirs()
    }

    private val file = File(dir, name)
    private val tmp = File(dir, "$name.tmp")

    @Synchronized
    fun read(): String? = runCatching {
        if (file.exists() && file.length() > 0) file.readText() else null
    }.getOrNull()

    @Synchronized
    fun write(text: String) {
        runCatching {
            tmp.writeText(text)
            if (!tmp.renameTo(file)) {
                file.writeText(text)
                tmp.delete()
            }
        }
    }

    fun lastModified(): Long = if (file.exists()) file.lastModified() else 0L

    @Synchronized
    fun delete() {
        file.delete()
    }
}
