package com.pbtservers.pbtmetagpt

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float {
        val x = a.luminance(); val y = b.luminance()
        return (maxOf(x, y) + 0.05f) / (minOf(x, y) + 0.05f)
    }
    @Test fun textosSemanticosDeAmbosTemasCumplenAA() {
        for (colors in listOf(PbtLightColors, PbtDarkColors)) {
            val pairs = listOf(colors.onBackground to colors.background, colors.onSurface to colors.surface,
                colors.onSurfaceVariant to colors.surfaceVariant, colors.primary to colors.background,
                colors.secondary to colors.surface, colors.onPrimary to colors.primary,
                colors.onSecondary to colors.secondary, colors.onError to colors.error,
                colors.onPrimaryContainer to colors.primaryContainer, colors.onSecondaryContainer to colors.secondaryContainer,
                colors.onErrorContainer to colors.errorContainer)
            pairs.forEach { (text, background) -> assertTrue("Contraste insuficiente: $text / $background", contrast(text, background) >= 4.5f) }
        }
    }
}
