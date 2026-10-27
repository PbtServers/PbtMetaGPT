package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.Lifecycle
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
            compose.onNodeWithText("Configuración inicial · 1/5 · Bienvenida").assertIsDisplayed()
            compose.onAllNodesWithText("Jarvis", substring = true).assertCountEquals(0)
            compose.onNodeWithText("Comenzar").performClick()
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextInput("Jarvis")
            compose.onNodeWithText("Guardar asistente y voz").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithText("Jarvis · marin · Atento Jarvis").assertIsDisplayed()
            assertEquals("Jarvis", ProfileStore(context).load().single().name)
            assertFalse(KeyVault(context).exists())
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 3/5 · Asistente").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("Jarvis")
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
            wake.test(AssistantProfile(name = "Jarvis"), ActivationPhrase(enabled = false))
            assertFalse(wake.armed)
            assertTrue(wake.status.value.contains("desactivada"))
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
            compose.onNodeWithText("Más ajustes").performClick()
            compose.onNode(hasText("Biblioteca de voces") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("API TTS externa · contrato PCM").performScrollTo().performClick()
            compose.onNodeWithText("Identificador de voz del proveedor").performScrollTo().performTextInput("personaje-original")
            compose.onNodeWithText("Guardar motor y voz en el asistente").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNodeWithText("Más ajustes").performClick()
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

    @Test fun setupReanudaVozRetrocedeYFinalizaSinGafasNiClave() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Comenzar").performClick()
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextInput("Luna")
            // El avance valida y guarda el perfil, sin exigir una sesión ni una clave real.
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("cedar").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(3, SetupStore(context).step)
                assertEquals("cedar", SetupStore(context).draft()?.voice)
            }
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 4/5 · Voz").assertIsDisplayed()
            compose.onNodeWithText("cedar").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Anterior").performClick()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("Luna")
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Continuar").performClick()
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 5/5 · Gafas Meta").assertIsDisplayed()
            compose.onNodeWithText("Omitir").performClick()
            assertTrue(context.getSharedPreferences("configuracion", 0).getBoolean("bienvenida", false))
            assertEquals("cedar", ProfileStore(context).load().single().voice)
            scenario.recreate()
            compose.onAllNodesWithText("Configuración inicial", substring = true).assertCountEquals(0)
            assertFalse(KeyVault(context).exists())
        }
    }

    @Test fun todasLasVocesRealtimePuedenPulsarseYGuardarSinAbrirAudio() {
        val profile = AssistantProfile(name = "Luna")
        ProfileStore(context).save(listOf(profile), profile.id)
        context.getSharedPreferences("configuracion", 0).edit().putBoolean("bienvenida", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // La barra inferior es fija: su destino no pertenece al formulario desplazable.
            compose.onNode(hasText("Voz") and hasClickAction()).assertIsDisplayed().performClick()
            RealtimeSettings.voices.forEach { voice ->
                compose.onNodeWithText(voice).performScrollTo().performClick().assertIsSelected()
                compose.onNodeWithText("Guardar motor y voz en el asistente").performScrollTo().performClick()
                compose.onNodeWithText("Entendido").performClick()
                assertEquals(voice, ProfileStore(context).load().single().voice)
            }
            scenario.recreate()
            assertEquals(RealtimeSettings.voices.last(), ProfileStore(context).load().single().voice)
        }
    }

    @Test fun detenerAntesDeRecomponerConservaLaUltimaVozDelSetup() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Comenzar").performClick()
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextInput("Luna")
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("marin").performScrollTo().assertIsSelected()
            compose.onNodeWithText("cedar").performScrollTo()
            compose.mainClock.autoAdvance = false
            try {
                // Ejecutar la acción accesible y detener Android antes del siguiente frame
                // de Compose: el guardado no debe depender de recomponer la pantalla.
                compose.onNodeWithText("cedar").performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(3, SetupStore(context).step)
                assertEquals("cedar", SetupStore(context).draft()?.voice)
            } finally { compose.mainClock.autoAdvance = true }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 4/5 · Voz").assertIsDisplayed()
            compose.onNodeWithText("cedar").performScrollTo().assertIsSelected()
        }
    }

    @Test fun borradorIncompletoPersisteYErrorNoReiniciaLaEtapa() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Comenzar").performClick()
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextInput("L")
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Entendido").performClick()
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 3/5 · Asistente").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("L")
            assertTrue(ProfileStore(context).load().isEmpty())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Configuración inicial · 3/5 · Asistente").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("L")
        }
    }

    @Test fun iconoAdaptativoEstaDeclaradoYResuelveEnAndroid() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotEquals(0, info.icon)
        assertTrue(context.packageManager.getApplicationIcon(info) is android.graphics.drawable.AdaptiveIconDrawable)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
    }
}
