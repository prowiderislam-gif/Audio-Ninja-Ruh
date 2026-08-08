package com.audioninja.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.audioninja.app.ui.screens.*
import com.audioninja.app.ui.theme.AudioNinjaTheme

sealed class Screen(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Record : Screen("record", "Record", Icons.Filled.Mic)
    object Library : Screen("library", "Library", Icons.Filled.LibraryMusic)
    object Favorites : Screen("favorites", "Favorites", Icons.Filled.Favorite)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

private val bottomNavItems = listOf(Screen.Record, Screen.Library, Screen.Favorites, Screen.Settings)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AudioNinjaTheme {
                AudioNinjaMainScreen()
            }
        }
    }
}

@Composable
fun AudioNinjaMainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    val selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) },
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Record.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Record.route) { RecordScreen() }
            composable(Screen.Library.route) { LibraryScreen(navController) }
            composable(Screen.Favorites.route) { LibraryScreen(navController, favoritesOnly = true) }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable("nowPlaying/{recordingId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("recordingId") ?: ""
                NowPlayingScreen(recordingId = id, navController = navController)
            }
            composable("about") { AboutScreen(navController) }
        }
    }
}
