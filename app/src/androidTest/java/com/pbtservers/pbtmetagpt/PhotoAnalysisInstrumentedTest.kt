package com.pbtservers.pbtmetagpt

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*

class PhotoAnalysisInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    @Test fun imagenYCredencialesPermitenConfirmarSinSesionNiEnvioAutomatico() {
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        var sends = 0
        compose.setContent { PbtTheme { Column {
            PhotoAnalysisPanel(image, true, VoiceState(), null,
                onConfirm = { assertSame(image, it); sends++ }, discard = {}, resume = {}, cancel = {})
        } } }
        compose.onNodeWithText("Preguntar a ChatGPT sobre esta foto").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertEquals(0, sends) }
        compose.onNodeWithText("Preguntar a ChatGPT sobre esta foto").performClick()
        compose.onNodeWithText("Enviar y preguntar").performClick()
        compose.runOnIdle { assertEquals(1, sends); assertFalse(image.isRecycled) }
    }
    @Test fun faltaDeConfiguracionExplicaElBloqueoYConservaLaImagen() {
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        compose.setContent { PbtTheme { Column {
            PhotoAnalysisPanel(image, false, VoiceState(), null,
                onConfirm = { fail("No debe enviarse sin configuración") }, discard = {}, resume = {}, cancel = {})
        } } }
        compose.onNodeWithText("Preguntar a ChatGPT sobre esta foto").assertIsNotEnabled()
        compose.onNodeWithText(PhotoAnalysisStatus.NEEDS_CONFIGURATION.message).assertIsDisplayed()
        assertFalse(image.isRecycled)
    }

    @Test fun fotoSobreviveADesconexionYRecreacionSinReenviarse() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("configuracion", 0).edit().clear().commit()
        val image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val memory = androidx.lifecycle.ViewModelProvider(activity)[PhotoMemory::class.java]
                memory.bitmap = image
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                val glasses = MetaGlasses(activity, scope, {}, photoMemory = memory)
                glasses.disconnect(); glasses.stopCapture()
                assertSame(image, glasses.state.value.photo)
                scope.cancel()
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val memory = androidx.lifecycle.ViewModelProvider(activity)[PhotoMemory::class.java]
                assertSame(image, memory.bitmap)
                assertFalse(image.isRecycled)
                memory.bitmap = null
            }
        }
        context.getSharedPreferences("configuracion", 0).edit().clear().commit()
    }

    @Test fun conversionJpegRespetaLimiteDelCanalYNoDestruyeLaFotoOriginal() {
        val image = Bitmap.createBitmap(1200, 600, Bitmap.Config.ARGB_8888)
        image.eraseColor(android.graphics.Color.BLUE)
        val base64 = ImageEncoding.jpeg(image)
        val bytes = android.util.Base64.decode(base64, android.util.Base64.NO_WRAP)
        assertTrue(bytes.size <= 42_000)
        assertEquals(0xff, bytes[0].toInt() and 0xff)
        assertEquals(0xd8, bytes[1].toInt() and 0xff)
        val decoded = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        assertNotNull(decoded)
        assertTrue(decoded.width <= 512 && decoded.height <= 512)
        assertFalse(image.isRecycled)
        val event = RealtimeProtocol.visualQuestion(base64)
        assertTrue(event.toByteArray(Charsets.UTF_8).size < 65_536)
        decoded.recycle(); image.recycle()
    }
}
