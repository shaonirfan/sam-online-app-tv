package com.shaonirfan.samonline.tv.data.remote

import com.shaonirfan.samonline.tv.data.model.Category

/**
 * The SAM Online "DHAKA-FLIX" BDIX servers. There is no API: every section is a
 * plain directory-listing page. These are the same nine sections BeeGear uses.
 */
object Servers {
    /** Settable only so JVM tests can point the app at a local fake server. */
    var categories: List<Category> = listOf(
        Category("english", "English Movies", "http://172.16.50.7/DHAKA-FLIX-7/English%20Movies/"),
        Category("english1080", "English Movies 1080p", "http://172.16.50.14/DHAKA-FLIX-14/English%20Movies%20%281080p%29/"),
        Category("imdb250", "IMDb Top 250", "http://172.16.50.14/DHAKA-FLIX-14/IMDb%20Top-250%20Movies/"),
        Category("hindi", "Hindi Movies", "http://172.16.50.14/DHAKA-FLIX-14/Hindi%20Movies/"),
        Category("south", "South Indian Hindi Dubbed", "http://172.16.50.14/DHAKA-FLIX-14/SOUTH%20INDIAN%20MOVIES/Hindi%20Dubbed/"),
        Category("series", "TV & Web Series", "http://172.16.50.12/DHAKA-FLIX-12/TV-WEB-Series/", isSeries = true),
        Category("anime", "Anime & Cartoon", "http://172.16.50.10/DHAKA-FLIX-10/Anime%20%26%20Cartoon%20TV%20Series/", isSeries = true),
        Category("korean", "Korean TV & Web Series", "http://172.16.50.14/DHAKA-FLIX-14/KOREAN%20TV%20%26%20WEB%20Series/", isSeries = true),
        Category("animation", "Animation Movies", "http://172.16.50.14/DHAKA-FLIX-14/Animation%20Movies/"),
    )
        internal set

    fun byId(id: String): Category? = categories.firstOrNull { it.id == id }

    fun categoryFor(url: String): Category? =
        categories.firstOrNull { url.startsWith(it.url, ignoreCase = true) }

    const val USER_AGENT = "Mozilla/5.0 (Linux; Android TV) SamOnlineTV/1.0"
}
