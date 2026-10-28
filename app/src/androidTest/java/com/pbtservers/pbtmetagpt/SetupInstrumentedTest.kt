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
        context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
        ProfileStore(context).save(emptyList(), null)
        context.getSharedPreferences("configuracion", 0).edit().clear().commit()
    }
    @After fun limpiar() = preparar()

    @Test fun bienvenidaYCreacionDeAsistenteSinBackendNiNombreImpuesto() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Configuración inicial · 1/3 · Servicio").assertIsDisplayed()
            compose.onAllNodesWithText("Jarvis", substring = true).assertCountEquals(0)
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextReplacement("Jarvis")
            compose.onNodeWithText("Guardar voz").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            assertEquals("Jarvis", ProfileStore(context).load().single().name)
            assertFalse(KeyVault(context).exists())
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 2/3 · Voz").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("Jarvis")
        }
    }

    @Test fun permisoDenegadoImpideEscuchaYConversacionYSuCierreEsIdempotente() {
        assertNotEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        instrumentation.runOnMainSync {
            var activated = false
            val wake = LocalWakeListener(context, scope) { _, _ -> activated = true }
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
            compose.onNode(hasText("Ajustes") and hasClickAction()).performClick()
            compose.onNodeWithText("Voz y escucha").performScrollTo().performClick()
            compose.onNode(hasText("Biblioteca de voces") and hasClickAction()).performScrollTo().performClick()
            abrirAvanzadas()
            compose.onNodeWithText("API TTS externa · contrato PCM").performScrollTo().performClick()
            compose.onNodeWithText("Identificador de voz del proveedor").performScrollTo().performTextInput("personaje-original")
            compose.onNodeWithText("Guardar motor y voz en el asistente").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            compose.onNode(hasText("Ajustes") and hasClickAction()).performClick()
            compose.onNodeWithText("Voz y escucha").performScrollTo().performClick()
            compose.onNode(hasText("Activación por voz") and hasClickAction()).performScrollTo().performClick()
            compose.onNodeWithText("Configuración avanzada de frases").performScrollTo().performClick()
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
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextReplacement("Luna")
            abrirAvanzadas()
            compose.onNodeWithText("cedar").performScrollTo().performClick().assertIsSelected()
            compose.runOnIdle {
                assertEquals(1, SetupStore(context).step)
                assertEquals("cedar", SetupStore(context).draft()?.voice)
            }
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 2/3 · Voz").assertIsDisplayed()
            abrirAvanzadas()
            compose.onNodeWithText("cedar").performScrollTo().assertIsSelected()
            compose.onNodeWithText("Anterior").performClick()
            compose.onNodeWithText("Configuración inicial · 1/3 · Servicio").assertIsDisplayed()
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("Luna")
            compose.onNodeWithText("Continuar").performClick()
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 3/3 · Escucha").assertIsDisplayed()
            compose.onNodeWithText("Omitir").performClick()
            assertTrue(context.getSharedPreferences("configuracion", 0).getBoolean("bienvenida", false))
            assertEquals("cedar", ProfileStore(context).load().single().voice)
            scenario.recreate()
            compose.onAllNodesWithText("Configuración inicial", substring = true).assertCountEquals(0)
            compose.onNode(hasText("Voces") and hasClickAction()).assertIsDisplayed()
            assertFalse(KeyVault(context).exists())
        }
    }

    @Test fun todasLasVocesRealtimePuedenPulsarseYGuardarSinAbrirAudio() {
        val profile = AssistantProfile(name = "Luna")
        ProfileStore(context).save(listOf(profile), profile.id)
        context.getSharedPreferences("configuracion", 0).edit().putBoolean("bienvenida", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Hay dos destinos clicables llamados Voz (panel inicial y barra).
            // Abrir la biblioteca por su etiqueta inequívoca.
            compose.onNode(hasText("Ajustes") and hasClickAction()).performClick()
            compose.onNodeWithText("Voz y escucha").performScrollTo().performClick()
            compose.onNode(hasText("Biblioteca de voces") and hasClickAction()).performScrollTo().performClick()
            abrirAvanzadas()
            RealtimeSettings.voices.forEach { voice ->
                // El resumen también muestra el nombre: solo el chip es clicable.
                compose.onNode(hasText(voice) and hasClickAction()).performScrollTo().performClick().assertIsSelected()
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
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextReplacement("Luna")
            abrirAvanzadas()
            // La prueba valida el callback encolado antes de recomponer,
            // no el desplazamiento: Android 16 puede invalidar ScrollTo al
            // cambiar la disposicion de FlowRow tras abrir avanzadas.
            compose.onNodeWithText("marin").assertIsSelected()
            compose.onNodeWithText("cedar").assertExists()
            compose.mainClock.autoAdvance = false
            try {
                compose.onNodeWithText("cedar").performSemanticsAction(SemanticsActions.OnClick) { click -> click() }
                scenario.moveToState(Lifecycle.State.CREATED)
                assertEquals(1, SetupStore(context).step)
                assertEquals("cedar", SetupStore(context).draft()?.voice)
            } finally { compose.mainClock.autoAdvance = true }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 2/3 · Voz").assertIsDisplayed()
            abrirAvanzadas()
            compose.onNodeWithText("cedar").assertIsSelected()
        }
    }

    @Test fun borradorIncompletoPersisteYErrorNoReiniciaLaEtapa() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Nombre del asistente").performScrollTo().performTextReplacement("L")
            compose.onNodeWithText("Continuar").performClick()
            compose.onNodeWithText("Entendido").performClick()
            scenario.recreate()
            compose.onNodeWithText("Configuración inicial · 2/3 · Voz").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("L")
            assertTrue(ProfileStore(context).load().isEmpty())
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            compose.onNodeWithText("Configuración inicial · 2/3 · Voz").assertIsDisplayed()
            compose.onNodeWithText("Nombre del asistente").assertTextContains("L")
        }
    }

    @Test fun cuatroApartadosYVozNaturalSinReproduccionAutomatica() {
        val profile = AssistantProfile(name = "Luna")
        ProfileStore(context).save(listOf(profile), profile.id)
        context.getSharedPreferences("configuracion", 0).edit().putBoolean("bienvenida", true).commit()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            listOf("Inicio", "Asistentes", "Voces", "Ajustes").forEach { tab ->
                compose.onNode(hasText(tab) and hasClickAction()).assertIsDisplayed()
            }
            compose.onNodeWithText("Hablar").assertIsDisplayed().assertIsNotEnabled()
            compose.onAllNodesWithText("Modelo Groq (ID público)").assertCountEquals(0)
            compose.onNode(hasText("Voces") and hasClickAction()).performClick()
            compose.onNodeWithText("Natural").performScrollTo().performClick().assertIsSelected()
            compose.onAllNodesWithText("URL HTTPS completa de la API de síntesis").assertCountEquals(0)
            compose.onAllNodesWithText("Escuchar muestra").onFirst().performScrollTo().assertIsNotEnabled()
            compose.onNodeWithText("Guardar voz").performScrollTo().performClick()
            compose.onNodeWithText("Entendido").performClick()
            val saved = ProfileStore(context).load().single()
            assertEquals("natural", saved.voiceMode)
            assertEquals("piper_es", saved.ttsEngine)
            assertEquals("es-ES", saved.language)
            assertEquals("davefx", saved.ttsVoice)
            compose.onAllNodesWithText("Android TTS", substring = true).assertCountEquals(0)
            assertFalse(HostedSettings(context).qwenAuthorized)
            assertFalse((context.applicationContext as PbtApplication).hosted.busy.value)
            // Despachador real de Activity: comprobar el BackHandler tras cerrar
            // el diálogo, sin enviar una tecla a una ventana en retirada.
            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Hablar").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Hablar").assertIsDisplayed()
        }
    }

    private fun abrirAvanzadas() {
        compose.onNodeWithText("Avanzada").performScrollTo().performClick()
    }

    @Test fun migraBorradorAnteriorSinPerderVozNiRepetirConfiguracionTerminada() {
        val draft = AssistantProfile(name = "Luna", voice = "cedar")
        context.getSharedPreferences("configuracion", 0).edit().putInt("setup_step", 3)
            .putString("setup_draft", draft.json().toString()).commit()
        val setup = SetupStore(context)
        assertEquals(1, setup.step)
        assertEquals("cedar", setup.draft()?.voice)
        setup.finish()
        assertTrue(context.getSharedPreferences("configuracion", 0).getBoolean("bienvenida", false))
        assertEquals(null, setup.draft())
    }

    @Test fun iconoAdaptativoEstaDeclaradoYResuelveEnAndroid() {
        val info = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertNotEquals(0, info.icon)
        assertTrue(context.packageManager.getApplicationIcon(info) is android.graphics.drawable.AdaptiveIconDrawable)
        assertNotNull(context.getDrawable(R.mipmap.ic_launcher_round))
    }
}
