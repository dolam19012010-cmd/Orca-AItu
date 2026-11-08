package com.orca.orcaauto.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = BackgroundDark,
    primaryContainer = PrimaryIndigoDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = AccentCyan,
    onSecondary = BackgroundDark,
    tertiary = AccentEmerald,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    onSurfaceVariant = TextSecondaryDark,
    outline = GlassBorder,
    outlineVariant = GlassBorderSubtle,
    error = AccentRose
)

@Composable
fun OrcaAutoTheme(
    darkTheme: Boolean = true, // Default to sleek modern dark theme
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            try {
                val window = (view.context as? Activity)?.window
                if (window != null) {
                    window.statusBarColor = BackgroundDark.toArgb()
                    window.navigationBarColor = BackgroundDark.toArgb()
                    WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                }
            } catch (e: Throwable) {
                // Ignore window insets styling exceptions on custom ROMs
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
