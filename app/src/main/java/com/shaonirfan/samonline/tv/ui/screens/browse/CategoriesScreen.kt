package com.shaonirfan.samonline.tv.ui.screens.browse

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.shaonirfan.samonline.tv.data.remote.Servers
import com.shaonirfan.samonline.tv.ui.components.FocusRestoreEffect
import com.shaonirfan.samonline.tv.ui.components.LocalAppContainer
import com.shaonirfan.samonline.tv.ui.components.PosterImage
import com.shaonirfan.samonline.tv.ui.components.initialFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocus
import com.shaonirfan.samonline.tv.ui.components.rememberFocusMemory
import com.shaonirfan.samonline.tv.ui.navigation.Routes
import com.shaonirfan.samonline.tv.ui.theme.Dimens
import com.shaonirfan.samonline.tv.ui.theme.SamColors

/** All nine DHAKA-FLIX sections as large tiles; each opens the section's browse grid. */
@Composable
fun CategoriesScreen(nav: NavController) {
    val container = LocalAppContainer.current
    val focus = rememberFocusMemory("categories")
    val previews = remember { container.catalog.cachedRows() }

    Column(Modifier.fillMaxSize()) {
        Text(
            "Categories",
            style = MaterialTheme.typography.headlineMedium,
            color = SamColors.TextPrimary,
            modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = Dimens.SafeVertical, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(Servers.categories, key = { _, it -> it.id }) { index, category ->
                val posters = previews[category.id].orEmpty().mapNotNull { it.poster }.take(3)
                Card(
                    onClick = { nav.navigate(Routes.folder(category.url, category.name, null)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .rememberFocus(focus, category.id)
                        .then(if (index == 0) Modifier.initialFocus(focus) else Modifier),
                    shape = CardDefaults.shape(RoundedCornerShape(14.dp)),
                    scale = CardDefaults.scale(focusedScale = Dimens.FocusScale),
                    border = CardDefaults.border(
                        focusedBorder = Border(BorderStroke(3.dp, SamColors.FocusRing), shape = RoundedCornerShape(14.dp)),
                    ),
                    glow = CardDefaults.glow(focusedGlow = Glow(SamColors.Accent.copy(alpha = 0.5f), 16.dp)),
                    colors = CardDefaults.colors(containerColor = SamColors.Surface),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        // A strip of the section's newest posters on the right.
                        Row(Modifier.align(Alignment.CenterEnd).fillMaxHeight()) {
                            posters.forEach { p -> PosterImage(p, "", Modifier.fillMaxHeight().width(70.dp)) }
                        }
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.horizontalGradient(
                                    0.35f to Color(0xFF1B1B24),
                                    1f to Color(0xFF1B1B24).copy(alpha = 0.2f),
                                )
                            )
                        )
                        Column(Modifier.align(Alignment.CenterStart).padding(20.dp)) {
                            Text(category.name, style = MaterialTheme.typography.titleLarge, color = SamColors.TextPrimary)
                            Text(
                                if (category.isSeries) "Series" else "Movies",
                                style = MaterialTheme.typography.bodyMedium, color = SamColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
    FocusRestoreEffect(focus)
}
