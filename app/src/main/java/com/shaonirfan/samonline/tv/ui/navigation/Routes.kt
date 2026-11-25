package com.shaonirfan.samonline.tv.ui.navigation

import android.net.Uri
import androidx.navigation.NavController
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.player.PlayItem
import com.shaonirfan.samonline.tv.player.PlaybackQueue

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val CATEGORIES = "categories"
    const val MY_LIST = "mylist"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val FOLDER = "folder?url={url}&title={title}&poster={poster}"
    const val PLAYER = "player"

    val topLevel = listOf(HOME, SEARCH, CATEGORIES, MY_LIST, DOWNLOADS, SETTINGS)

    fun folder(url: String, title: String, poster: String?): String =
        "folder?url=${Uri.encode(url)}&title=${Uri.encode(title)}&poster=${Uri.encode(poster ?: "")}"
}

fun NavController.openItem(item: MediaItem) {
    if (item.isVideo) {
        playSingle(item.url, item.title, item.poster)
    } else {
        navigate(Routes.folder(item.url, item.title, item.poster))
    }
}

fun NavController.play(queue: List<PlayItem>, startIndex: Int, startPositionMs: Long? = null) {
    if (queue.isEmpty()) return
    PlaybackQueue.set(queue, startIndex, startPositionMs)
    navigate(Routes.PLAYER) { launchSingleTop = true }
}

fun NavController.playSingle(url: String, title: String, poster: String?) {
    val parent = url.substringBeforeLast('/') + "/"
    play(listOf(PlayItem(url, title, seriesTitle = "", parentUrl = parent, poster = poster)), 0)
}

fun NavController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
