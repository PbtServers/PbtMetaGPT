package com.pbtservers.pbtmetagpt

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

/** Contratos del dispositivo/emulador. No acredita reconocimiento hablado ni Bluetooth. */
class ListeningInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val app get() = context.applicationContext as PbtApplication
    @After fun cleanup() {
        instrumentation.runOnMainSync { ContinuousWakeService.stop(context); app.hosted.stop(); app.voice.stop() }
        context.getSharedPreferences("voz_escucha", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSystemService(NotificationManager::class.java).cancel(72)
        context.getSystemService(NotificationManager::class.java).cancel(73)
    }
    @Test fun preferenciasPersistenSinArrancarCapturaNiServicio() {
        val expected = ListeningOptions(continuous = true, background = true, sensitivity = 0.75f,
            inactivitySeconds = 180, endPhrase = "fin de sesión", goodbye = true)
        ListeningSettings(context).save(expected)
        assertEquals(expected, ListeningSettings(context).load())
        assertFalse(ContinuousWakeService.running)
        assertNull(app.microphone.owner())
    }
    @Test fun inicioSinActividadVisibleSeRechazaSinMicrofono() {
        instrumentation.runOnMainSync {
            val previous = app.activityVisible
            app.activityVisible = false
            try {
                try { ContinuousWakeService.startVisible(context); fail("Debe exigir actividad visible") }
                catch (_: IllegalStateException) { }
                assertFalse(ContinuousWakeService.running)
                assertNull(app.microphone.owner())
            } finally { app.activityVisible = previous }
        }
    }
    @Test fun ausenciaDeChatGptNoCreaIntentsPrivadosNiFalla() {
        // La imagen limpia de Google APIs no incluye ChatGPT. Si alguien lo instala,
        // se comprueba el launcher público, sin lanzar la actividad externa en la suite.
        val integration = OfficialChatGpt(context)
        val intent = integration.launchIntent()
        if (intent == null) {
            assertTrue(integration.open(visible = true).contains("no está instalado"))
        } else {
            assertEquals(OfficialChatGpt.PACKAGE, intent.component?.packageName)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        assertNull(app.microphone.owner())
    }
    @Test fun asistenteDelTelefonoSoloUsaAccionesPublicasSinBusquedaWeb() {
        val launcher = SystemAssistantLauncher(context)
        val intent = launcher.launchIntent()
        if (intent == null) assertTrue(launcher.open(true).startsWith("No hay una acción pública"))
        else {
            assertTrue(intent.action in setOf(Intent.ACTION_VOICE_COMMAND, Intent.ACTION_ASSIST))
            if (intent.action == Intent.ACTION_VOICE_COMMAND) assertEquals(SystemAssistantLauncher.GOOGLE, intent.`package`)
            assertNull(intent.data); assertNull(intent.extras)
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        }
        assertFalse(ContinuousWakeService.running); assertNull(app.microphone.owner())
    }
    @Test fun comandosLocalesYRetornoNoCapturanMicrofonoPorSiMismos() {
        assertEquals(LocalVoiceCommand.CHATGPT, VoiceCommands.parse("Jarvis, abre GPT Live", "Jarvis", ListeningOptions()))
        assertFalse(ContinuousWakeService.running)
        assertNull(app.microphone.owner())
        ListeningSettings(context).save(ListeningOptions(background = true))
        assertTrue(ListeningSettings(context).load().background)
        assertNull(app.microphone.owner())
    }
    @Test fun permisoDenegadoNoPermiteArrancarCapturaVosk() = runBlocking<Unit> {
        assertNotEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        try { HostedDictation(context).capture(continuous = true); fail("Debe fallar antes de capturar") }
        catch (problem: HostedFailure) { assertTrue(problem.publicMessage.contains("permiso")) }
        assertNull(app.microphone.owner())
    }
}
