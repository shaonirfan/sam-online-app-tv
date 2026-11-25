package com.shaonirfan.samonline.tv.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Remembers which element of a screen had focus (survives navigation through the
 * back stack via rememberSaveable) and puts focus back on it when the user returns.
 */
class FocusMemory internal constructor(
    private val saved: MutableState<String?>,
    /** Key that was focused when the screen was last left: the one to restore. */
    val restoreKey: String?,
) {
    val requester = FocusRequester()
    val initialRequester = FocusRequester()
    var restored = false
        internal set

    fun onFocused(key: String) {
        saved.value = key
    }

    val hasSavedFocus: Boolean get() = restoreKey != null
}

@Composable
fun rememberFocusMemory(screenKey: String): FocusMemory {
    val saved = rememberSaveable(screenKey) { mutableStateOf<String?>(null) }
    return remember(screenKey) { FocusMemory(saved, saved.value) }
}

/** Tag a focusable element with a stable key so it can be restored later. */
fun Modifier.rememberFocus(memory: FocusMemory, key: String): Modifier {
    val base = if (key == memory.restoreKey) this.focusRequester(memory.requester) else this
    return base.onFocusChanged { if (it.isFocused) memory.onFocused(key) }
}

/** Marks the element that should get focus the first time the screen is shown. */
fun Modifier.initialFocus(memory: FocusMemory): Modifier = this.focusRequester(memory.initialRequester)

/**
 * Restores focus to the remembered element, or to the initial element on first visit.
 * Retries for a few frames because lazy lists compose their items asynchronously.
 * Pass a [ready] flag that becomes true once the screen's content is loaded.
 */
@Composable
fun FocusRestoreEffect(memory: FocusMemory, ready: Boolean = true) {
    LaunchedEffect(ready) {
        if (!ready || memory.restored) return@LaunchedEffect
        repeat(20) {
            withFrameNanos { }
            val target = if (memory.hasSavedFocus) memory.requester else memory.initialRequester
            if (runCatching { target.requestFocus() }.isSuccess) {
                memory.restored = true
                return@LaunchedEffect
            }
        }
        // The remembered element is gone (e.g. list changed): fall back to the initial one.
        runCatching { memory.initialRequester.requestFocus() }
        memory.restored = true
    }
}
