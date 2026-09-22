package com.firefly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Firefly palette: warm amber glow on a night-sky background. Every container
// role is set explicitly so no Material baseline lavender leaks through.
private val Amber = Color(0xFFFFB300)
private val AmberDeep = Color(0xFF8A6100)
private val AmberPale = Color(0xFFFFE2A8)
private val AmberShadow = Color(0xFF4A3300)
private val Night = Color(0xFF0E1116)
private val NightSurface = Color(0xFF181C24)
private val NightSurfaceHigh = Color(0xFF232935)
private val Cream = Color(0xFFFFFBF2)
private val CreamSurface = Color(0xFFF6EFE0)
private val Ink = Color(0xFF1C1A16)
private val Paper = Color(0xFFECE6DA)
private val Sky = Color(0xFF4FC3F7)
private val SkyDeep = Color(0xFF01579B)
private val Alert = Color(0xFFD64545)
private val AlertPale = Color(0xFFFFDAD6)
private val AlertDeep = Color(0xFF410002)

private val DarkColors = darkColorScheme(
    primary = Amber, onPrimary = Night,
    primaryContainer = AmberShadow, onPrimaryContainer = AmberPale,
    secondary = Sky, onSecondary = Night,
    secondaryContainer = Color(0xFF0B3A52), onSecondaryContainer = Color(0xFFCBEBFF),
    tertiary = Color(0xFFD6C6A5), onTertiary = Night,
    tertiaryContainer = Color(0xFF3B3222), onTertiaryContainer = Color(0xFFF1E3C4),
    error = Color(0xFFFF6B6B), onError = Night,
    errorContainer = Color(0xFF5C1B1B), onErrorContainer = AlertPale,
    background = Night, onBackground = Paper,
    surface = NightSurface, onSurface = Paper,
    surfaceVariant = NightSurfaceHigh, onSurfaceVariant = Color(0xFFC9C2B4),
    surfaceContainer = NightSurfaceHigh, surfaceContainerHigh = Color(0xFF2B3240), surfaceContainerHighest = Color(0xFF333B4A), surfaceContainerLow = Color(0xFF12161D),
    outline = Color(0xFF6B6455), outlineVariant = Color(0xFF3A3F4A),
)

private val LightColors = lightColorScheme(
    primary = AmberDeep, onPrimary = Color.White,
    primaryContainer = AmberPale, onPrimaryContainer = AmberShadow,
    secondary = SkyDeep, onSecondary = Color.White,
    secondaryContainer = Color(0xFFCBEBFF), onSecondaryContainer = Color(0xFF002B44),
    tertiary = Color(0xFF6B5B3A), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E3C4), onTertiaryContainer = Color(0xFF2A2210),
    error = Alert, onError = Color.White,
    errorContainer = AlertPale, onErrorContainer = AlertDeep,
    background = Cream, onBackground = Ink,
    surface = Color.White, onSurface = Ink,
    surfaceVariant = CreamSurface, onSurfaceVariant = Color(0xFF52493A),
    surfaceContainer = CreamSurface, surfaceContainerHigh = Color(0xFFEFE6D2), surfaceContainerHighest = Color(0xFFE8DEC6), surfaceContainerLow = Cream,
    outline = Color(0xFF847A66), outlineVariant = Color(0xFFD8CFBC),
)

@Composable
fun FireflyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // No dynamic colour: high-contrast dots on the map matter more than wallpaper matching.
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
