package com.shaonirfan.samonline.tv

import android.os.SystemClock
import android.util.Log

/**
 * One-line "ready" markers for the on-device test script (tools/tv-test/run_test.sh).
 * The script waits for these in logcat instead of guessing with fixed delays, then checks
 * the facts they carry (titles, sizes, counts). Format: `key=value` pairs, values URL-free.
 */
object TestLog {
    const val TAG = "SamTvTest"
    private val start = SystemClock.elapsedRealtime()

    fun mark(event: String, vararg fields: Pair<String, Any?>) {
        val t = SystemClock.elapsedRealtime() - start
        val body = fields.joinToString(" ") { (k, v) -> "$k=${v.toString().replace(' ', '_').take(120)}" }
        Log.i(TAG, "$event t=$t $body".trim())
    }
}

/**
 * Test entry points: `adb shell am start -n com.shaonirfan.samonline.tv/.MainActivity --es open <target>`
 *
 *   home                      Home screen
 *   search:<query>            Search with the query typed in
 *   category:<categoryId>     a category's browse grid (ids: english, english1080, imdb250, hindi,
 *                             south, series, anime, korean, animation)
 *   title:<categoryId>:<n>    details page of the n-th title (0-based) in that category's Home row
 *   play:<categoryId>:<n>     start playing that title (resume point / first episode)
 *
 * Navigation only; nothing here changes data, so it is safe to leave in release builds.
 */
object TestHooks {
    /** A query the Search screen should start with (consumed once). */
    @Volatile
    var pendingSearch: String? = null
}
