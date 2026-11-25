package com.jarvis.assistant.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = JarvisNeonBlue,
    secondary = JarvisCyan,
    tertiary = JarvisAccent,
    background = JarvisBg,
    surface = JarvisPanel,
    onPrimary = JarvisBgDeep,
    onSecondary = JarvisBgDeep,
    onBackground = JarvisText,
    onSurface = JarvisText
)

private val LightColorScheme = lightColorScheme(
    primary = JarvisAccent,
    secondary = JarvisNeonBlue,
    tertiary = JarvisCyan,
    background = JarvisText,
    surface = Color(0xFFFFFFFF),
    onBackground = JarvisBgDeep,
    onSurface = JarvisBgDeep
)

import androidx.compose.ui.graphics.Color

@Composable
fun JarvisTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = JarvisTypography, content = content)
}