package com.firefly.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Firefly palette: warm amber glow on a night-sky background.
private val Amber = Color(0xFFFFB300)
private val AmberDim = Color(0xFF8A6100)
private val Night = Color(0xFF0E1116)
private val NightSurface = Color(0xFF181C24)

private val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = Night,
    background = Night,
    onBackground = Color(0xFFECE6DA),
    surface = NightSurface,
    onSurface = Color(0xFFECE6DA),
)

private val LightColors = lightColorScheme(
    primary = AmberDim,
    onPrimary = Color.White,
    background = Color(0xFFFFFBF2),
    onBackground = Night,
    surface = Color(0xFFFFFFFF),
    onSurface = Night,
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
