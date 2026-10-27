package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Before fun preparar() {
        KeyVault(context).delete()
        ProfileStore(context).save(emptyList(), null)
        context.getSharedPreferences("configuracion", 0).edit().clear().commit()
    }
    @After fun limpiar() = preparar()

    @Test fun bienvenidaYCreacionDeAsistenteSinBackendNiNombreImpuesto() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Configuración inicial · 1/12 · Bienvenida").assertIsDisplayed()
            compose.onAllNodesWithText("Jarvis", substring = true).assertCountEquals(0)
            repeat(4) { compose.onNodeWithText("Siguiente").performClick() }
            compose.onNodeWithText("Crear Personalizado").performScrollTo().performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextInput("Jarvis")
            compose.onNodeWithText("Guardar asistente y voz").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithText("Jarvis · marin · Atento Jarvis").assertIsDisplayed()
            assertEquals("Jarvis", ProfileStore(context).load().single().name)
            assertFalse(KeyVault(context).exists())
            scenario.recreate()
            compose.onNodeWithText("Jarvis · marin · Atento Jarvis").assertIsDisplayed()
        }
    }

    @Test fun permisoDenegadoImpideEscuchaYConversacionYSuCierreEsIdempotente() {
        assertNotEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        instrumentation.runOnMainSync {
            var activated = false
            val wake = LocalWakeListener(context, scope) { activated = true }
            val voice = RealtimeClient(context, scope, {})
            voice.configure(AssistantProfile(name = "Jarvis")) { error("No debe leerse una clave sin permiso de micrófono") }
            wake.start(AssistantProfile(name = "Jarvis"))
            assertFalse(wake.armed)
            assertFalse(activated)
            voice.start("", "", false)
            assertFalse(voice.state.value.connecting)
            assertFalse(voice.state.value.active)
            repeat(2) { wake.stop(); voice.stop() }
            assertFalse(wake.armed)
            assertTrue(wake.status.value.contains("desactivada"))
        }
        scope.cancel()
    }
    @Test fun bibliotecaYFrasesGuardanConfiguracionesIndependientesSinClave() {
        val first = AssistantProfile(name = "Jarvis")
        val second = AssistantProfile(name = "Friday", activationPhrases = listOf(ActivationPhrase(text = "Escucha")))
        ProfileStore(context).save(listOf(first, second), first.id)
        context.getSharedPreferences("configuracion", 0).edit().putBoolean("bienvenida", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNode(hasText("Biblioteca de voces") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("API TTS externa · contrato PCM").performScrollTo().performClick()
            compose.onNodeWithText("Identificador de voz del proveedor").performScrollTo().performTextInput("personaje-original")
            compose.onNodeWithText("Guardar motor y voz en el asistente").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNode(hasText("Activación por voz") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("Texto de la frase").performScrollTo().performTextClearance()
            compose.onNodeWithText("Texto de la frase").performTextInput("Oye")
            compose.onNodeWithText("Guardar frases y preferencias").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            val profiles = ProfileStore(context).load()
            assertEquals("external", profiles.first { p -> p.id == first.id }.ttsEngine)
            assertEquals("personaje-original", profiles.first { p -> p.id == first.id }.ttsVoice)
            assertEquals("Oye Jarvis", profiles.first { p -> p.id == first.id }.phrase)
            assertEquals("Escucha Friday", profiles.first { p -> p.id == second.id }.phrase)
            assertFalse(KeyVault(context).exists())
        }
    }
}
