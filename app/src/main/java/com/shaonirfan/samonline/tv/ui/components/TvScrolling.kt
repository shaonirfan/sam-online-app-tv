package com.shaonirfan.samonline.tv.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * TV "pivot" scrolling for vertical screens (what TvLazyColumn's pivotOffsets used to
 * do): the focused row is kept ~[parentFraction] from the top instead of the list only
 * scrolling when focus reaches the screen edge.
 */
@OptIn(ExperimentalFoundationApi::class)
class PivotBringIntoViewSpec(private val parentFraction: Float) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val leadingEdge = containerSize * parentFraction
        if (size >= containerSize - leadingEdge) return offset // too tall: align its top
        return offset - leadingEdge
    }
}

/** Horizontal rows: scroll the minimum amount needed to fully show the focused poster. */
@OptIn(ExperimentalFoundationApi::class)
object MinimalBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        val trailing = offset + size
        return when {
            offset >= 0f && trailing <= containerSize -> 0f
            offset < 0f || size > containerSize -> offset
            else -> trailing - containerSize
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PivotScrolling(parentFraction: Float = 0.12f, content: @Composable () -> Unit) {
    val spec = remember(parentFraction) { PivotBringIntoViewSpec(parentFraction) }
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MinimalScrolling(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides MinimalBringIntoViewSpec, content = content)
}
