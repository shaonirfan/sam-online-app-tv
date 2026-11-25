package com.shaonirfan.samonline.tv.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import androidx.tv.material3.rememberDrawerState
import com.shaonirfan.samonline.tv.player.PlayerScreen
import com.shaonirfan.samonline.tv.ui.screens.browse.CategoriesScreen
import com.shaonirfan.samonline.tv.ui.screens.details.FolderScreen
import com.shaonirfan.samonline.tv.ui.screens.downloads.DownloadsScreen
import com.shaonirfan.samonline.tv.ui.screens.home.HomeScreen
import com.shaonirfan.samonline.tv.ui.screens.mylist.MyListScreen
import com.shaonirfan.samonline.tv.ui.screens.search.SearchScreen
import com.shaonirfan.samonline.tv.ui.screens.settings.SettingsScreen
import com.shaonirfan.samonline.tv.ui.theme.SamColors

private data class DrawerEntry(val route: String, val label: String, val icon: ImageVector)

private val drawerEntries = listOf(
    DrawerEntry(Routes.SEARCH, "Search", Icons.Filled.Search),
    DrawerEntry(Routes.HOME, "Home", Icons.Filled.Home),
    DrawerEntry(Routes.CATEGORIES, "Categories", Icons.Filled.GridView),
    DrawerEntry(Routes.MY_LIST, "My List", Icons.Filled.LibraryAdd),
    DrawerEntry(Routes.DOWNLOADS, "Downloads", Icons.Filled.Download),
    DrawerEntry(Routes.SETTINGS, "Settings", Icons.Filled.Settings),
)

/** Width of the collapsed navigation rail; top-level screens start to the right of it. */
val RailWidth = 72.dp

@Composable
fun SamNavHost(nav: NavHostController) {
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { fadeIn(tween(250)) },
        exitTransition = { fadeOut(tween(200)) },
        popEnterTransition = { fadeIn(tween(250)) },
        popExitTransition = { fadeOut(tween(200)) },
        modifier = Modifier.fillMaxSize().background(SamColors.Background),
    ) {
        composable(Routes.HOME) { TopLevel(Routes.HOME, nav) { HomeScreen(nav) } }
        composable(Routes.SEARCH) { TopLevel(Routes.SEARCH, nav) { SearchScreen(nav) } }
        composable(Routes.CATEGORIES) { TopLevel(Routes.CATEGORIES, nav) { CategoriesScreen(nav) } }
        composable(Routes.MY_LIST) { TopLevel(Routes.MY_LIST, nav) { MyListScreen(nav) } }
        composable(Routes.DOWNLOADS) { TopLevel(Routes.DOWNLOADS, nav) { DownloadsScreen(nav) } }
        composable(Routes.SETTINGS) { TopLevel(Routes.SETTINGS, nav) { SettingsScreen() } }
        composable(
            Routes.FOLDER,
            arguments = listOf(
                navArgument("url") { type = NavType.StringType; defaultValue = "" },
                navArgument("title") { type = NavType.StringType; defaultValue = "" },
                navArgument("poster") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val args = entry.arguments
            FolderScreen(
                nav = nav,
                url = args?.getString("url").orEmpty(),
                title = args?.getString("title").orEmpty(),
                posterHint = args?.getString("poster")?.ifBlank { null },
            )
        }
        composable(Routes.PLAYER) { PlayerScreen(onExit = { nav.popBackStack() }) }
    }
}

/** Wraps a top-level destination in the collapsible left navigation rail. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TopLevel(route: String, nav: NavHostController, content: @Composable () -> Unit) {
    if (route != Routes.HOME) {
        BackHandler { nav.navigateTopLevel(Routes.HOME) }
    }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val selectedRequester = remember { FocusRequester() }
    ModalNavigationDrawer(
        drawerState = drawerState,
        scrimBrush = Brush.horizontalGradient(listOf(SamColors.Background, SamColors.Background.copy(alpha = 0.6f))),
        drawerContent = { value ->
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(SamColors.Background.copy(alpha = 0.95f), SamColors.Background.copy(alpha = 0f))
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 27.dp)
                    .selectableGroup()
                    .focusRestorer(selectedRequester),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (value == DrawerValue.Open) "SAM Online" else "S",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = SamColors.Accent,
                    modifier = Modifier.padding(start = 14.dp, bottom = 24.dp),
                )
                Spacer(Modifier.height(24.dp))
                drawerEntries.forEach { entry ->
                    val selected = entry.route == route
                    NavigationDrawerItem(
                        selected = selected,
                        onClick = {
                            if (!selected) nav.navigateTopLevel(entry.route)
                            drawerState.setValue(DrawerValue.Closed)
                        },
                        leadingContent = { Icon(entry.icon, contentDescription = entry.label) },
                        colors = NavigationDrawerItemDefaults.colors(
                            selectedContainerColor = SamColors.Accent.copy(alpha = 0.25f),
                            focusedContainerColor = androidx.compose.ui.graphics.Color.White,
                            focusedContentColor = androidx.compose.ui.graphics.Color.Black,
                        ),
                        modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier,
                    ) {
                        Text(entry.label)
                    }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().padding(start = RailWidth)) { content() }
    }
}
