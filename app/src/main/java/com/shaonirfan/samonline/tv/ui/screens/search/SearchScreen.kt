package com.shaonirfan.samonline.tv.ui.screens.search

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SpaceBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.FilterChip
import androidx.tv.material3.FilterChipDefaults
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.R
import com.shaonirfan.samonline.tv.ui.components.FocusMemory
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.MinimalScrolling
import com.shaonirfan.samonline.tv.ui.components.PivotScrolling
import com.shaonirfan.samonline.tv.ui.components.PosterCard
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.navigation.openItem
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors

private val keyRows = listOf("abcdef", "ghijkl", "mnopqr", "stuvwx", "yz1234", "567890")

/**
 * Netflix-style search: an on-screen D-pad keyboard on the left (plus voice search and
 * hardware-keyboard / number-key input), poster results on the right.
 */
@Composable
fun SearchScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container) }
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val index by vm.indexState.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val shown = results.filtered(filter)
    val context = LocalContext.current
    val focus = rememberFocusMemory("search")

    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::set)
        }
    }
    fun startVoice() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.voice_search_prompt))
        try {
            voiceLauncher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "Voice search isn't available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { e ->
                // Physical keyboards / remotes with number keys type straight into the query.
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Backspace -> { vm.backspace(); true }
                    e.key == Key.Search -> { startVoice(); true }
                    else -> {
                        val c = e.nativeKeyEvent.unicodeChar.toChar()
                        if (c.isLetterOrDigit() || c == ' ') { vm.type(c.lowercaseChar()); true } else false
                    }
                }
            }
            .padding(top = Dimens.SafeVertical),
    ) {
        // -------------------------------------------------------------- keyboard
        Column(
            Modifier
                .width(360.dp)
                .fillMaxHeight()
                .padding(start = Dimens.SafeHorizontal - 16.dp, end = 12.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SamColors.Surface)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = SamColors.TextSecondary)
                Spacer(Modifier.width(10.dp))
                Text(
                    query.ifEmpty { "Search movies & series" },
                    style = MaterialTheme.typography.titleMedium,
                    color = if (query.isEmpty()) SamColors.TextMuted else SamColors.TextPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (query.isNotEmpty()) Text("|", color = SamColors.Accent, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            Column(Modifier.focusRestorer(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KeyButton(Icons.Filled.SpaceBar, "Space", 2, focus) { vm.type(' ') }
                    KeyButton(Icons.AutoMirrored.Filled.Backspace, "Delete", 2, focus) { vm.backspace() }
                    KeyButton(Icons.Filled.Clear, "Clear", 1, focus) { vm.clear() }
                    KeyButton(Icons.Filled.KeyboardVoice, "Voice", 1, focus) { startVoice() }
                }
                keyRows.forEachIndexed { r, row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEachIndexed { c, ch ->
                            CharKey(
                                ch, focus,
                                modifier = if (r == 0 && c == 0) Modifier.initialFocus(focus) else Modifier,
                            ) { vm.type(ch) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                when {
                    index.building && !index.ready -> "Indexing library… ${index.count} titles so far"
                    index.building -> "${index.count} titles · refreshing index…"
                    index.ready -> "${index.count} titles indexed"
                    else -> "Preparing search index…"
                },
                style = MaterialTheme.typography.bodySmall, color = SamColors.TextMuted,
            )
        }

        // -------------------------------------------------------------- results
        Box(Modifier.weight(1f).fillMaxHeight()) {
            when {
                query.isBlank() -> Hint("Type with the keyboard or press the mic to search", Modifier.align(Alignment.Center))
                results.items.isEmpty() && results.query == query -> Hint(
                    if (index.building && !index.ready) "Still indexing… results will appear as titles are found"
                    else "No results for “$query”",
                    Modifier.align(Alignment.Center),
                )
                else -> Column(Modifier.fillMaxSize()) {
                    if (results.suggestions.isNotEmpty()) {
                        MinimalScrolling {
                            LazyRow(
                                contentPadding = PaddingValues(start = 16.dp, end = Dimens.SafeHorizontal, bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.focusRestorer(),
                            ) {
                                lazyItems(results.suggestions, key = { it }) { suggestion ->
                                    SuggestionRow(suggestion, query, focus) { vm.set(suggestion.lowercase()) }
                                }
                            }
                        }
                    }
                    Row(
                        Modifier.padding(start = 16.dp, bottom = 6.dp).focusRestorer(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        SearchFilter.entries.forEach { f ->
                            FilterChip(
                                selected = f == filter,
                                onClick = { vm.setFilter(f) },
                                modifier = Modifier.rememberFocus(focus, "filter|${f.name}"),
                                colors = FilterChipDefaults.colors(
                                    selectedContainerColor = SamColors.Accent,
                                    selectedContentColor = Color.White,
                                    focusedContainerColor = Color.White,
                                    focusedContentColor = Color.Black,
                                    focusedSelectedContainerColor = Color.White,
                                    focusedSelectedContentColor = Color.Black,
                                ),
                            ) { Text("${f.label} (${results.count(f)})", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                    if (shown.isEmpty()) {
                        Hint("No ${filter.label.lowercase()} match “$query”", Modifier.padding(start = 16.dp))
                    }
                    PivotScrolling(0.08f) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        contentPadding = PaddingValues(start = 16.dp, end = Dimens.SafeHorizontal, top = 8.dp, bottom = 40.dp),
                        verticalArrangement = Arrangement.spacedBy(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(shown, key = { it.url }) { item ->
                            Box(contentAlignment = Alignment.TopCenter) {
                                PosterCard(
                                    item = item,
                                    width = 130.dp,
                                    caption = item.subtitle + (item.year?.let { " · $it" } ?: ""),
                                    onClick = { nav.openItem(item) },
                                    modifier = Modifier.rememberFocus(focus, "r|" + item.url),
                                )
                            }
                        }
                    }
                    }
                }
            }
        }
    }
    FocusRestoreEffect(focus)
}

@Composable
private fun SuggestionRow(text: String, query: String, focus: FocusMemory, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.height(40.dp).rememberFocus(focus, "sug|$text"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SamColors.Surface,
            contentColor = SamColors.TextSecondary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Row(Modifier.height(40.dp).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            // Typed part normal, the completion bold (Netflix-style).
            val cut = text.lowercase().indexOf(query.lowercase().trim()).takeIf { it >= 0 }
            val annotated = buildAnnotatedString {
                if (cut == null) append(text) else {
                    val end = (cut + query.trim().length).coerceAtMost(text.length)
                    append(text.substring(0, end))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(text.substring(end)) }
                }
            }
            Text(annotated, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Hint(text: String, modifier: Modifier) {
    Text(text, style = MaterialTheme.typography.bodyLarge, color = SamColors.TextMuted, modifier = modifier.padding(24.dp))
}

@Composable
private fun CharKey(ch: Char, focus: FocusMemory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(44.dp).rememberFocus(focus, "key|$ch"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SamColors.Surface,
            contentColor = SamColors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(ch.uppercase(), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun KeyButton(icon: ImageVector, label: String, span: Int, focus: FocusMemory, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .height(44.dp)
            .width(44.dp * span + 8.dp * (span - 1))
            .rememberFocus(focus, "key|$label"),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SamColors.SurfaceRaised,
            contentColor = SamColors.TextPrimary,
            focusedContainerColor = Color.White,
            focusedContentColor = Color.Black,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp))
        }
    }
}
