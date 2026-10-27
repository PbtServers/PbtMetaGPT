package com.pbtservers.pbtmetagpt

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun raizOscuraPropagaTextoClaro() = checkTheme("oscuro", PbtDarkColors.onBackground)
    @Test fun raizClaraPropagaTextoOscuro() = checkTheme("claro", PbtLightColors.onBackground)
    private fun checkTheme(mode: String, expected: Color) {
        var inherited = Color.Unspecified
        compose.setContent { PbtTheme(mode) { inherited = LocalContentColor.current; Text("Texto legible") } }
        compose.onNodeWithText("Texto legible").assertIsDisplayed()
        compose.runOnIdle { assertEquals(expected, inherited) }
    }
}
