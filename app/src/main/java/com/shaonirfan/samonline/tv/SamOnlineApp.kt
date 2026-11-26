package com.shaonirfan.samonline.tv

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy
import com.shaonirfan.samonline.tv.data.remote.Servers
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class SamOnlineApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Loads the saved search index now; a refresh crawl (if it's over a day old) waits 60 s.
        container.search.warmUp(delayMs = 60_000)
    }

    /** Coil with a 25% memory cache and a 512 MB disk cache for posters/backdrops. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient {
            OkHttpClient.Builder()
                .connectTimeout(8, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", Servers.USER_AGENT).build())
                }
                .build()
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("poster_cache"))
                .maxSizeBytes(512L * 1024 * 1024)
                .build()
        }
        .memoryCachePolicy(CachePolicy.ENABLED)
        .diskCachePolicy(CachePolicy.ENABLED)
        .respectCacheHeaders(false)
        .crossfade(250)
        .build()
}
