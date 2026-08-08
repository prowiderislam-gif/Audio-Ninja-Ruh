package com.audioninja.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val AudioNinjaDarkScheme = darkColorScheme(
    primary = NeonRed,
    onPrimary = OnDarkPrimary,
    secondary = WineRed,
    onSecondary = OnDarkPrimary,
    background = NinjaBlack,
    onBackground = OnDarkPrimary,
    surface = NinjaSurface,
    onSurface = OnDarkPrimary,
    surfaceVariant = NinjaSurfaceElevated,
    onSurfaceVariant = OnDarkSecondary,
    error = NeonRedBright,
    outline = DividerRed
)

@Composable
fun AudioNinjaTheme(
    // App is dark-by-default per brand spec; ignoring system light theme intentionally.
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? android.app.Activity)?.window
        window?.let {
            it.statusBarColor = NinjaBlack.toArgb()
            WindowCompat.getInsetsController(it, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AudioNinjaDarkScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
