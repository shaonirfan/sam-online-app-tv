package com.shaonirfan.samonline.tv.ui.screens.mylist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.data.model.MediaItem
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.navigation.openItem
import com.shaonirfan.samonline.tv.ui.screens.details.BrowseGrid
import com.shaonirfan.samonline.tv.ui.theme.SamColors

@Composable
fun MyListScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val saved by container.library.myList.collectAsStateWithLifecycle()
    val focus = rememberFocusMemory("mylist")

    if (saved.isEmpty()) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your list is empty", style = MaterialTheme.typography.headlineSmall, color = SamColors.TextPrimary)
            Text(
                "Open any movie or series and choose “My List” to save it here.",
                style = MaterialTheme.typography.bodyLarge, color = SamColors.TextSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        return
    }
    BrowseGrid(
        title = "My List",
        items = saved.map { MediaItem(it.title, it.url, it.poster, it.subtitle) },
        focus = focus,
        onClick = { nav.openItem(it) },
    )
    FocusRestoreEffect(focus)
}
