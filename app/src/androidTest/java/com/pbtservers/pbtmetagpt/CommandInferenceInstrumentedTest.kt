package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.vosk.LibVosk
import org.vosk.LogLevel

/** PCM de Piper es-ES real → Vosk real. No emite sonido ni acredita ruta física, eco o gafas. */
class CommandInferenceInstrumentedTest {
    @Test fun palabraParaAisladaYFraseNegativaConInferenciaReal() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wake = WakeModel(context)
        val model = SpainVoiceModel(context)
        val engine = SpainVoiceEngine(context)
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        try {
            wake.delete(); model.delete()
            withContext(Dispatchers.IO) {
                wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
                model.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            }
            assertEquals(WakeModel.SHA256, wake.digest())
            val voice = VoiceModes.spain(AssistantProfile(name = "Jarvis")).validated()
            val positive = engine.synthesize(voice, "Para.")
            val negative = engine.synthesize(voice, "Esto es para mañana.")
            val recognized = decode(wake, positive)
            assertTrue("Vosk debe reconocer la orden aislada sintetizada por Piper", recognized.any {
                InterruptionGate().accept(it.text, it.confidence, 5000, 0, true, "Jarvis", ListeningOptions()) == LocalVoiceCommand.INTERRUPT
            })
            assertTrue("La frase normal no debe interrumpir", decode(wake, negative).none {
                VoiceCommands.parse(it.text, "Jarvis", ListeningOptions()) == LocalVoiceCommand.INTERRUPT
            })
            // La misma hipótesis reconocida queda bloqueada por la política de eco.
            assertTrue(recognized.none {
                InterruptionGate().accept(it.text, it.confidence, 5000, 0, false, "Jarvis", ListeningOptions()) != null
            })
            positive.bytes.fill(0); negative.bytes.fill(0)
            assertNull((context.applicationContext as PbtApplication).microphone.owner())
        } finally { engine.close(); model.delete(); wake.delete() }
    }
    private suspend fun decode(wake: WakeModel, audio: PcmAudio): List<CommandHypothesis> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CommandHypothesis>()
        LocalCommandDecoder(wake.model.absolutePath, audio.sampleRate.toFloat()).use { decoder ->
            val buffer = ShortArray(audio.sampleRate / 50)
            var offset = 0
            while (offset < audio.bytes.size) {
                val n = minOf(buffer.size, (audio.bytes.size - offset) / 2)
                repeat(n) { index ->
                    buffer[index] = ((audio.bytes[offset + index * 2].toInt() and 255) or (audio.bytes[offset + index * 2 + 1].toInt() shl 8)).toShort()
                }
                decoder.accept(buffer, n)?.let(results::add)
                offset += n * 2
            }
            buffer.fill(0)
            repeat(150) { decoder.accept(buffer, buffer.size)?.let(results::add) }
        }
        results
    }
}
