package com.pbtservers.pbtmetagpt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ErrorLogInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun informePrivadoSobreviveYLaPantallaPermiteBorrarloConConfirmacion() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PbtApplication
        app.journal.clear()
        app.journal.record(Check.REALTIME, Fault.INITIALIZATION, IllegalStateException("contenido que no debe exportarse"))
        try {
            compose.setContent { PbtTheme { ErrorLogScreen {} } }
            compose.onNodeWithText("Exportar registros .txt").assertIsDisplayed().assertIsEnabled()
            compose.waitUntil(5000) { compose.onAllNodes(hasText("IllegalStateException", substring = true)).fetchSemanticsNodes().isNotEmpty() }
            assertFalse(app.journal.report().contains("contenido que no debe exportarse"))
            compose.onNodeWithText("Borrar historial de errores").performClick()
            compose.onNodeWithText("¿Borrar registros?").assertIsDisplayed()
            compose.onNodeWithText("Borrar", useUnmergedTree = true).performClick()
            compose.waitUntil(5000) { !app.journal.report().contains("INITIALIZATION") }
        } finally { app.journal.clear() }
    }
}
