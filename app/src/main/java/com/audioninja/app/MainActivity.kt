package com.audioninja.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audioninja.app.data.SettingsRepository
import com.audioninja.app.service.FloatingBubbleService
import com.audioninja.app.ui.screens.*
import com.audioninja.app.ui.theme.AudioNinjaTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

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

    override fun onStop() {
        super.onStop()
        // App is going to background (minimized, or user switched away) — bring
        // the bubble back if the user has it enabled in Settings, so it's never
        // permanently gone just because it was dragged off once.
        lifecycleScope.launch {
            try {
                val settingsRepo = SettingsRepository(applicationContext)
                val enabled = settingsRepo.floatingBubbleEnabled.first()
                if (enabled && Settings.canDrawOverlays(applicationContext)) {
                    startService(Intent(applicationContext, FloatingBubbleService::class.java))
                }
            } catch (_: Exception) { }
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
            composable(Screen.Record.route) { RecordScreen(navController = navController) }
            composable(Screen.Library.route) { LibraryScreen(navController) }
            composable(Screen.Favorites.route) { LibraryScreen(navController, favoritesOnly = true) }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable("nowPlaying/{recordingId}") { backStackEntry ->
                val id = backStackEntry.arguments?.getString("recordingId") ?: ""
                NowPlayingScreen(recordingId = id, navController = navController)
            }
            composable("about") { AboutScreen(navController) }
            composable("playlists") { PlaylistsScreen(navController) }
            composable(
                "playlistDetail/{playlistId}",
                arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
            ) { backStackEntry ->
                val id = backStackEntry.arguments?.getString("playlistId") ?: ""
                PlaylistDetailScreen(playlistId = id, navController = navController)
            }
            composable(
                "playlistPlayer/{playlistId}/{trackId}",
                arguments = listOf(
                    navArgument("playlistId") { type = NavType.StringType },
                    navArgument("trackId") { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val playlistId = backStackEntry.arguments?.getString("playlistId") ?: ""
                val trackId = backStackEntry.arguments?.getString("trackId") ?: ""
                PlaylistPlayerScreen(playlistId = playlistId, trackId = trackId, navController = navController)
            }
        }
    }
}
