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
        val engine = SpainVoiceEngine(context, reproducibleFixture = true)
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
            val repeated = engine.synthesize(voice, "Para.")
            try { assertArrayEquals("La señal de la fixture debe ser reproducible", positive.bytes, repeated.bytes) }
            finally { repeated.bytes.fill(0) }
            val recognized = decode(wake, positive)
            assertTrue("Vosk debe reconocer la orden aislada sintetizada por Piper: ${recognized.map { Triple(it.text, it.confidence, it.onsetSeconds) }}; PCM=${positive.bytes.size}", recognized.any {
                InterruptionGate().accept(it.text, it.confidence, 5000, 0, true, "Jarvis", ListeningOptions()) == LocalVoiceCommand.INTERRUPT
            })
            val negatives = listOf("Esto es para mañana.", "Para mañana.", "Prepara la cena.", "No para.", "Para y dime otra cosa.", "Pero.")
            for (phrase in negatives) {
                val pcm = engine.synthesize(voice, phrase)
                try { assertTrue("La frase normal no debe interrumpir: $phrase", decode(wake, pcm).none {
                    VoiceCommands.parse(it.text, "Jarvis", ListeningOptions()) == LocalVoiceCommand.INTERRUPT
                }) } finally { pcm.bytes.fill(0) }
            }
            // La misma hipótesis reconocida queda bloqueada por la política de eco.
            assertTrue(recognized.none {
                InterruptionGate().accept(it.text, it.confidence, 5000, 0,
                    !VoiceCommands.matchesEcho(it.text, "Para."), "Jarvis", ListeningOptions()) != null
            })
            positive.bytes.fill(0)
            assertNull((context.applicationContext as PbtApplication).microphone.owner())
        } finally { engine.close(); model.delete(); wake.delete() }
    }
    @Test fun ordenesChatGptReconocidasSinAceptarNegacionesNiFrasesIncrustadas() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wake = WakeModel(context)
        val model = SpainVoiceModel(context)
        val engine = SpainVoiceEngine(context, reproducibleFixture = true)
        try {
            withContext(Dispatchers.IO) {
                wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
                model.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            }
            val voice = VoiceModes.spain(AssistantProfile(name = "Jarvis")).validated()
            val positive = listOf("Abre GPT Live.", "Inicia GPT Live.", "Abre ChatGPT.", "Inicia ChatGPT en voz.", "Abre ChatGPT en voz.", "Quiero hablar con ChatGPT.")
            val negative = listOf("No quiero hablar con ChatGPT.", "Explícame cómo abre GPT Live.", "Esto es para mañana.", "Quiero hablar contigo.")
            for (phrase in positive + negative) {
                val pcm = engine.synthesize(voice, phrase)
                try {
                    val heard = decode(wake, pcm)
                    val opens = heard.any { VoiceCommands.parse(it.text, "Jarvis", ListeningOptions()) == LocalVoiceCommand.CHATGPT }
                    assertEquals("Fixture fija $phrase → ${heard.map { Triple(it.text, it.confidence, it.onsetSeconds) }}", phrase in positive, opens)
                } finally { pcm.bytes.fill(0) }
            }
        } finally { engine.close(); model.delete(); wake.delete() }
    }
    @Test fun ordenesDeMusicaReconocidasLocalmenteSinAceptarNegaciones() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wake = WakeModel(context)
        val model = SpainVoiceModel(context)
        val engine = SpainVoiceEngine(context, reproducibleFixture = true)
        try {
            withContext(Dispatchers.IO) {
                wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
                model.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            }
            val voice = VoiceModes.spain(AssistantProfile(name = "Jarvis")).validated()
            val positive = mapOf("Reproduce música." to MusicAction.PLAY, "Pon Spotify." to MusicAction.PLAY,
                "Pausa la música." to MusicAction.PAUSE, "Siguiente canción." to MusicAction.NEXT,
                "Canción anterior." to MusicAction.PREVIOUS)
            val negative = listOf("No reproduce música.", "No pongas Spotify.", "Explica la siguiente canción.")
            for (phrase in positive.keys + negative) {
                val pcm = engine.synthesize(voice, phrase)
                try {
                    val heard = decode(wake, pcm)
                    val accepted = heard.mapNotNull {
                        InterruptionGate().accept(it.text, it.confidence, 5000, 0, true, "Jarvis", ListeningOptions())
                            ?.let(MusicCommands::action)
                    }
                    assertEquals("Fixture fija $phrase → ${heard.map { it.text to it.confidence }}", positive[phrase], accepted.firstOrNull())
                } finally { pcm.bytes.fill(0) }
            }
        } finally { engine.close(); model.delete(); wake.delete() }
    }
    private suspend fun decode(wake: WakeModel, audio: PcmAudio): List<CommandHypothesis> = withContext(Dispatchers.IO) {
        val results = mutableListOf<CommandHypothesis>()
        LocalCommandDecoder(wake.model.absolutePath, audio.sampleRate.toFloat(), evaluation = { open, restricted, bounds ->
            android.util.Log.i("PbtCommandTest", "Fixture fija: libre=$open; restringido=$restricted; voz=$bounds")
        }).use { decoder ->
            val buffer = ShortArray(audio.sampleRate / 50)
            // El detector real ya recibe silencio/ruido antes de una orden. El
            // arranque directo con una palabra corta omite esa precaptura acústica.
            repeat(50) { decoder.accept(buffer, buffer.size) }
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
            decoder.finish().takeIf { it.text.isNotBlank() }?.let(results::add)
        }
        results
    }
}
