package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

val PbtDarkColors = darkColorScheme(
    primary = Color(0xFF9BC4FF), onPrimary = Color(0xFF002F65),
    secondary = Color(0xFFB6C5FF), onSecondary = Color(0xFF182A59),
    background = Color(0xFF080B10), onBackground = Color(0xFFF3F6FA),
    surface = Color(0xFF17212E), onSurface = Color(0xFFE3E9F0),
    surfaceVariant = Color(0xFF35434D), onSurfaceVariant = Color(0xFFD0DCE5),
)
val PbtLightColors = lightColorScheme(
    primary = Color(0xFF0055B8), onPrimary = Color.White,
    secondary = Color(0xFF405A91), onSecondary = Color.White,
    background = Color(0xFFF5F8FC), onBackground = Color(0xFF17212E),
    surface = Color(0xFFFFFFFF), onSurface = Color(0xFF17212E),
    surfaceVariant = Color(0xFFE0E8EF), onSurfaceVariant = Color(0xFF35434D),
)

@Composable
fun PbtTheme(mode: String = "sistema", content: @Composable () -> Unit) {
    val dark = when (mode) { "oscuro" -> true; "claro" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) PbtDarkColors else PbtLightColors) {
        // El fondo por sí solo no establece LocalContentColor: Surface sí lo hace.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground, content = content)
    }
}
