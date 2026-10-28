package com.pbtservers.pbtmetagpt

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Recorre selección → muestra → AudioTrack con pesos auténticos y sin proveedores. */
class LocalVoiceUiInstrumentedTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun seleccionaKokoroReproduceLaMuestraYPersisteAlRecrear() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as PbtApplication
        val model = KokoroModel(context)
        val preferences = context.getSharedPreferences("configuracion", 0)
        try {
            instrumentation.runOnMainSync { application.voice.stop(); application.hosted.stop(); application.clearMessage() }
            KeyVault(context).delete()
            context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
            preferences.edit().clear().putBoolean("bienvenida", true).commit()
            model.install(source = { instrumentation.context.assets.open("kokoro/${it.name}") })
            val profile = VoiceModes.spain(AssistantProfile(name = "Luna"))
            ProfileStore(context).save(listOf(profile), profile.id)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                compose.onNode(hasText("Voces") and hasClickAction()).performClick()
                compose.onNodeWithText("Otras voces locales · Kokoro").performScrollTo().performClick()
                compose.onNodeWithContentDescription("Escuchar muestra de Dora").performScrollTo().assertIsEnabled().performClick()
                compose.waitUntil(60_000) {
                    application.voice.state.value.failed ||
                        application.voice.state.value.status.startsWith("Reproduciendo voz alternativa")
                }
                assertFalse(application.voice.state.value.failed)
                assertTrue(application.voice.state.value.paused) // La muestra no abre el micrófono.
                compose.waitUntil(60_000) { !application.voice.state.value.active }
                assertFalse(application.voice.state.value.failed)
                compose.onAllNodesWithText("Entendido").assertCountEquals(0)
                assertFalse(KeyVault(context).exists())
                assertFalse(HostedSettings(context).authorized)
                val saved = ProfileStore(context).load().single()
                assertEquals("kokoro", saved.ttsEngine)
                assertEquals("ef_dora", saved.ttsVoice)
                scenario.recreate()
                compose.onNodeWithText("Natural").assertIsSelected()
                compose.onNodeWithContentDescription("Escuchar muestra de Dora").performScrollTo().assertIsEnabled()
                compose.onAllNodesWithText("Android TTS", substring = true).assertCountEquals(0)
            }
        } finally {
            instrumentation.runOnMainSync { application.voice.stop(); application.hosted.stop(); application.clearMessage() }
            model.delete()
            ProfileStore(context).save(emptyList(), null)
            preferences.edit().clear().commit()
            context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
        }
    }

    @Test fun muestraSinModeloTerminaConErrorYLiberacion() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val application = context.applicationContext as PbtApplication
        KokoroModel(context).delete()
        try {
            instrumentation.runOnMainSync {
                application.voice.stop()
                application.voice.previewAlternative(VoiceModes.kokoro(AssistantProfile(name = "Luna")), false)
            }
            compose.waitUntil(10_000) { application.voice.state.value.failed }
            assertFalse(application.voice.state.value.active)
            assertFalse(application.voice.state.value.responding)
        } finally { instrumentation.runOnMainSync { application.voice.stop(); application.clearMessage() } }
    }
}
