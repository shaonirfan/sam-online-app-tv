package com.shaonirfan.samonline.tv

import android.content.Context
import com.shaonirfan.samonline.tv.data.download.DownloadManager
import com.shaonirfan.samonline.tv.data.local.JsonFileStore
import com.shaonirfan.samonline.tv.data.local.LibraryStore
import com.shaonirfan.samonline.tv.data.local.PosterStore
import com.shaonirfan.samonline.tv.data.local.SettingsStore
import com.shaonirfan.samonline.tv.data.repository.CatalogRepository
import com.shaonirfan.samonline.tv.data.repository.PlaybackResolver
import com.shaonirfan.samonline.tv.data.repository.SearchRepository

/** Manual dependency container (the app is small enough not to need Hilt). */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val posters = PosterStore(app)
    val library = LibraryStore(app)
    val settings = SettingsStore(app)
    val catalog = CatalogRepository(posters, JsonFileStore(app, "home_rows.json"))
    val search = SearchRepository(JsonFileStore(app, "search_index.json"), posters)
    val downloads = DownloadManager(app)
    val playback = PlaybackResolver(catalog, library)
}
