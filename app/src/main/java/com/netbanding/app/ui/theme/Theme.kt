package com.netbanding.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = Ember,
    onPrimary = Charcoal,
    primaryContainer = EmberDeep,
    onPrimaryContainer = EmberSoft,
    secondary = Cloudy,
    onSecondary = Charcoal,
    tertiary = EmberSoft,
    background = Charcoal,
    onBackground = Bone,
    surface = Hearth,
    onSurface = Bone,
    surfaceVariant = Ash,
    onSurfaceVariant = Bone,
    surfaceContainerHighest = Ash,
    outline = Pebble,
    outlineVariant = Pebble,
)

private val LightColors = lightColorScheme(
    primary = Crail,
    onPrimary = Paper,
    primaryContainer = CrailSoft,
    onPrimaryContainer = Bark,
    secondary = Cloudy,
    onSecondary = Bark,
    tertiary = CrailDeep,
    background = Sand,
    onBackground = Bark,
    surface = CardWhite,
    onSurface = Bark,
    surfaceVariant = Pampas,
    onSurfaceVariant = Fawn,
    surfaceContainerLowest = CardWhite,
    surfaceContainerLow = CardWhite,
    surfaceContainer = CardWhite,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Pampas,
    outline = Cloudy,
    outlineVariant = Cloudy,
)

@Composable
fun TemplateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content,
    )
}
