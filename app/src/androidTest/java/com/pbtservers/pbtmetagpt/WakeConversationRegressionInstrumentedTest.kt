package com.pbtservers.pbtmetagpt

import android.Manifest
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** Servicio real y ASR nativo con PCM sintético; proveedor/TTS controlados solo en debug. */
class WakeConversationRegressionInstrumentedTest {
    @get:Rule val permissions: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    @Test fun activacionNormalAvisoBloqueadoYDesactivadoLleganADictadoYRespuestaInclusoEnSegundoPlano() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as PbtApplication
        val voice = SpainVoiceEngine(context, reproducibleFixture = true)
        val wake = WakeModel(context)
        val model = SpainVoiceModel(context)
        val vault = KeyVault(context, provider = "groq")
        val questions = Channel<ShortArray>(2)
        val spoken = CopyOnWriteArrayList<String>()
        val requests = CopyOnWriteArrayList<JSONObject>()
        val captures = AtomicInteger()
        val frames = AtomicInteger()
        var wakePcm = ShortArray(0)
        var question = ShortArray(0)
        fun shell(command: String) { instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        } }
        var stage = "Inicio"
        suspend fun await(condition: () -> Boolean) {
            try { withTimeout(12000) { while (!condition()) delay(20) } }
            catch (timeout: TimeoutCancellationException) { throw AssertionError(stage + "; estado=" + ContinuousWakeService.state.value + "; mic=" + app.microphone.owner() + "; capturas=" + captures.get() + "; peticiones=" + requests.size + "; proveedor=" + app.hosted.state.value.status + "; error=" + ContinuousWakeService.error.value + "\\n" + app.voiceTransitions.report(), timeout) }
        }
        try {
            wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
            model.install(source = { instrumentation.context.assets.open("piper-es/" + it.name) })
            val profile = VoiceModes.spain(AssistantProfile(name = "Jarvis"), "davefx").validated()
            wakePcm = pcm16k(voice.synthesize(profile, "Atento Jarvis."))
            question = pcm16k(voice.synthesize(profile, "Qué tiempo hará mañana en Barcelona."))
            voice.close()
            vault.save("gsk_" + "prueba".repeat(5))
            HostedSettings(context).apply { select("groq"); saveGroq("openai/gpt-oss-20b", true) }
            ListeningSettings(context).save(ListeningOptions(continuous = true, background = true, interruption = false))
            context.getSharedPreferences("configuracion", 0).edit().putBoolean("audio-bluetooth", false).commit()
            DevelopmentVoice.api = GroqApi(OkHttpClient.Builder().addInterceptor { chain ->
                val buffer = okio.Buffer()
                chain.request().body!!.writeTo(buffer)
                requests += JSONObject(buffer.readUtf8())
                val answer = "**Respuesta importante**. *Espera...* Todo está preparado."
                val event = JSONObject().put("choices", org.json.JSONArray().put(JSONObject().put("delta", JSONObject().put("content", answer))))
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("Prueba")
                    .header("Content-Type", "text/event-stream")
                    .body(("data: " + event + "\n\ndata: [DONE]\n\n").toResponseBody("text/event-stream".toMediaType())).build()
            }.build())
            DevelopmentVoice.synthesis = { _, phrase ->
                spoken += phrase
                PcmAudio(ByteArray(3200), 16000)
            }
            DevelopmentVoice.capture = { request ->
                val lease = requireNotNull(app.microphone.acquire(MicrophoneOwner.HOSTED_LOCAL))
                try {
                    assertEquals("es-ES", request.language)
                    request.ready(); captures.incrementAndGet()
                    val samples = questions.receive()
                    request.started()
                    val text = withContext(Dispatchers.IO) {
                        app.voskModels.acquire(wake.model.absolutePath).use { cached ->
                            VoskDictationDecoder(cached.model).use { decoder ->
                                val block = ShortArray(320)
                                var offset = 0
                                while (offset < samples.size) {
                                    ensureActive()
                                    val count = minOf(block.size, samples.size - offset)
                                    samples.copyInto(block, 0, offset, offset + count)
                                    decoder.accept(block, count); offset += count
                                }
                                block.fill(0); repeat(150) { decoder.accept(block, block.size) }
                                decoder.finish().text
                            }
                        }
                    }
                    request.ended()
                    text
                } finally { app.microphone.release(lease) }
            }
            // Misma implementación del servicio, tres escenarios independientes.
            for (mode in 0..2) {
                captures.set(0); requests.clear(); spoken.clear(); frames.set(Int.MIN_VALUE)
                DevelopmentVoice.wakeFrames = null
                ProfileStore(context).save(listOf(profile.copy(confirmationSound = mode != 2)), profile.id)
                DevelopmentVoice.confirmation = if (mode == 1) ({ awaitCancellation() }) else ({ delay(20) })
                val input: (ShortArray) -> Int = { target ->
                    target.fill(0)
                    val offset = frames.get()
                    if (offset < 0 || offset >= wakePcm.size) 0 else {
                        val count = minOf(target.size, (wakePcm.size - offset).coerceAtLeast(0))
                        if (count > 0) wakePcm.copyInto(target, 0, offset, offset + count)
                        frames.addAndGet(target.size)
                        target.size
                    }
                }
                ActivityScenario.launch(MainActivity::class.java).use { activity ->
                    stage = "Iniciar detector, escenario " + mode
                    activity.onActivity { ContinuousWakeService.startVisible(it) }
                    await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
                    if (mode == 1) {
                        activity.moveToState(Lifecycle.State.CREATED)
                        shell("input keyevent KEYCODE_SLEEP")
                    }
                    try {
                        stage = "Activar conversación, escenario " + mode
                        frames.set(0)
                        DevelopmentVoice.wakeFrames = input
                        await { captures.get() == 1 }
                        DevelopmentVoice.wakeFrames = null
                        assertTrue(ContinuousWakeService.authorizedRunning)
                        assertNotEquals(ListeningPhase.SUSPENDED, ContinuousWakeService.state.value)
                        stage = "Primera respuesta y nueva escucha, escenario " + mode
                        questions.send(question)
                        await { requests.size == 1 && captures.get() >= 2 }
                        assertNull("La respuesta no puede ocultar un fallo de AudioTrack", app.hosted.audioError.value)
                        assertTrue(app.hosted.messages.value.any { it.assistant && it.text.contains("**Respuesta") })
                        assertTrue(spoken.isNotEmpty())
                        assertFalse(spoken.joinToString().contains('*'))
                        // Control de interrupción real del controlador; su precisión acústica
                        // se prueba aparte en CommandInferenceInstrumentedTest.
                        stage = "Interrumpir y volver a capturar, escenario " + mode
                        withContext(Dispatchers.Main) { app.hosted.interrupt() }
                        await { captures.get() >= 3 }
                        stage = "Segunda respuesta, escenario " + mode
                        questions.send(question)
                        await { requests.size == 2 && captures.get() >= 4 }
                        assertNull(app.hosted.audioError.value)
                        assertTrue(requests.last().getJSONArray("messages").toString().contains("assistant"))
                        stage = "Regresar a espera, escenario " + mode
                        withContext(Dispatchers.Main) { app.hosted.stop() }
                        await { ContinuousWakeService.state.value == ListeningPhase.LISTENING }
                        assertTrue(ContinuousWakeService.authorizedRunning)
                        assertEquals(MicrophoneOwner.CONTINUOUS_LOCAL, app.microphone.owner())
                        android.util.Log.i("PbtAsrTest", JSONObject().put("escenario_servicio", mode)
                            .put("aviso_bloqueado", mode == 1).put("respuestas_controladas", requests.size)
                            .put("capturas", captures.get()).put("audio_fisico_verificado", false).toString())
                    } finally {
                        shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
                        withContext(Dispatchers.Main) { ContinuousWakeService.stop(context); app.hosted.stop() }
                        await { !ContinuousWakeService.running && app.microphone.owner() == null }
                    }
                }
            }
        } finally {
            DevelopmentVoice.reset(); questions.close(); wakePcm.fill(0); question.fill(0)
            withContext(Dispatchers.Main) { ContinuousWakeService.stop(context); app.hosted.stop(); app.voice.stop() }
            app.hosted.awaitReleased(); voice.close(); model.delete(); wake.delete(); vault.delete()
            ProfileStore(context).save(emptyList(), null)
            context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
            context.getSharedPreferences("voz_escucha", 0).edit().clear().commit()
            shell("input keyevent KEYCODE_WAKEUP"); shell("wm dismiss-keyguard")
        }
    }
    private fun pcm16k(audio: PcmAudio): ShortArray {
        val count = audio.bytes.size / 2
        fun value(i: Int) = ((audio.bytes[i * 2].toInt() and 255) or (audio.bytes[i * 2 + 1].toInt() shl 8)).toShort().toInt()
        return try {
            ShortArray((count.toLong() * 16000 / audio.sampleRate).toInt() + 48000) { i ->
                val position = i.toDouble() * audio.sampleRate / 16000
                if (position >= count - 1) 0 else {
                    val left = position.toInt()
                    (value(left) + (value(left + 1) - value(left)) * (position - left)).toInt().toShort()
                }
            }
        } finally { audio.bytes.fill(0) }
    }
}
