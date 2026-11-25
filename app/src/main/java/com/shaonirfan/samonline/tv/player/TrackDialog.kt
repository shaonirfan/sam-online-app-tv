package com.shaonirfan.samonline.tv.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.ui.theme.SamColors

/** D-pad list dialog used for subtitle, audio and speed selection. Opens focused on the current choice. */
@Composable
fun TrackDialog(
    title: String,
    options: List<Pair<String, Boolean>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedFocus = remember { FocusRequester() }
    val selectedIndex = options.indexOfFirst { it.second }.coerceAtLeast(0)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .width(440.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SamColors.Surface)
                .padding(24.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = SamColors.TextPrimary)
            Spacer(Modifier.height(14.dp))
            if (options.isEmpty()) {
                Text("Nothing to choose for this video.", style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary)
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.heightIn(max = 360.dp),
            ) {
                itemsIndexed(options) { index, (label, selected) ->
                    ListItem(
                        selected = selected,
                        onClick = { onSelect(index) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier),
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = SamColors.SurfaceRaised,
                            focusedContainerColor = Color.White,
                            focusedContentColor = Color.Black,
                            focusedSelectedContainerColor = Color.White,
                            focusedSelectedContentColor = Color.Black,
                        ),
                        headlineContent = { Text(label, style = MaterialTheme.typography.titleMedium) },
                        trailingContent = {
                            if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", modifier = Modifier.size(22.dp))
                        },
                    )
                }
            }
        }
    }
    LaunchedEffect(options.size) {
        if (options.isNotEmpty()) runCatching { selectedFocus.requestFocus() }
    }
}
