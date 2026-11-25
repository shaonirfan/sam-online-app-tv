package com.shaonirfan.samonline.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.ui.theme.Dimens

/**
 * A titled horizontal row of posters. Uses Compose foundation's LazyRow (the
 * replacement Google recommends for the removed TvLazyRow); focusRestorer() makes
 * focus re-enter the row on the last focused poster instead of the first one.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ContentRow(
    title: String,
    rowKey: String,
    items: List<MediaItem>,
    focusMemory: FocusMemory,
    onClick: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    progressFor: (MediaItem) -> Float? = { null },
    onFocused: (MediaItem) -> Unit = {},
    firstItemModifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        SectionTitle(title)
        MinimalScrolling {
        LazyRow(
            state = rememberLazyListState(),
            contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(Dimens.RowSpacing),
            modifier = Modifier.fillMaxWidth().focusRestorer(),
        ) {
            itemsIndexed(items, key = { _, it -> it.url }) { index, item ->
                val key = "$rowKey|${item.url}"
                PosterCard(
                    item = item,
                    onClick = { onClick(item) },
                    progress = progressFor(item),
                    onFocused = onFocused,
                    modifier = Modifier
                        .rememberFocus(focusMemory, key)
                        .then(if (index == 0) firstItemModifier else Modifier),
                )
            }
        }
        }
    }
}
