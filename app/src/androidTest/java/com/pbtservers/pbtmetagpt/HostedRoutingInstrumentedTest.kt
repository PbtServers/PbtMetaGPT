package com.pbtservers.pbtmetagpt
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** Controlador real: cualquier intento HTTP queda contado y rechazado. */
class HostedRoutingInstrumentedTest {
    @Test fun comandosLleganAlControlMusicalYLaOrdenDudosaNoRompeLaSesionNiLlamaAGroq() = runBlocking<Unit> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as PbtApplication
        val vault = KeyVault(context, provider = "groq")
        val settings = HostedSettings(context)
        val attempts = AtomicInteger()
        val api = GroqApi(OkHttpClient.Builder().addInterceptor {
            attempts.incrementAndGet(); throw java.io.IOException("Peticiones prohibidas en esta prueba local")
        }.build())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val conversation = HostedConversation(context, scope, api)
        suspend fun await(condition: () -> Boolean) { withTimeout(8000) { while (!condition()) delay(20) } }
        try {
            vault.save("gsk_" + "prueba".repeat(5))
            settings.select("groq"); settings.saveGroq("openai/gpt-oss-20b", true)
            MusicSettings(context).enable(true)
            for (phrase in listOf("Pon música", "Ponme música", "Pon música en Spotify", "Pausa Spotify", "Continúa la música", "Siguiente canción", "Canción anterior")) {
                withContext(Dispatchers.Main) {
                    app.clearMessage()
                    conversation.start(AssistantProfile(name = "Jarvis").validated())
                    conversation.send(phrase)
                }
                await { app.message.value != null && !conversation.busy.value }
                assertTrue(app.message.value.orEmpty(), app.message.value.orEmpty().let { it.startsWith("Autoriza") || it.startsWith("No hay una sesión") })
                assertTrue(conversation.messages.value.isEmpty())
                assertEquals(0, attempts.get())
            }
            withContext(Dispatchers.Main) { conversation.send("Pon música en Spotify por ahí") }
            await { !conversation.busy.value }
            assertFalse(conversation.state.value.failed)
            assertFalse(conversation.state.value.paused)
            assertTrue(conversation.commandStatus.value.contains("Repítela"))
            assertEquals(0, attempts.get())
            withContext(Dispatchers.Main) { conversation.send("Termina") }
            await { !conversation.state.value.active }
        } finally {
            withContext(Dispatchers.Main) { conversation.stop(); app.hosted.stop(); app.voice.stop() }
            conversation.awaitReleased(); scope.cancel(); vault.delete(); MusicSettings(context).enable(false)
            context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
        }
    }
}
