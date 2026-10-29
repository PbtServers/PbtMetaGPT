package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model

/** Piper/Vosk auténticos por la misma compuerta de producción; sin AudioRecord ni red. */
class WakeInferenceInstrumentedTest {
    @Test fun esperaSinAsrYConservaActivacionYPreguntaTrasAbrirLaCompuerta() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val wake = WakeModel(context)
        val store = SpainVoiceModel(context)
        val engine = SpainVoiceEngine(context, reproducibleFixture = true)
        try {
            wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
            store.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            val profile = VoiceModes.spain(AssistantProfile(name = "Luna")).validated()
            val gate = ActivationGate()
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            withContext(Dispatchers.IO) {
                Model(wake.model.absolutePath).use { model ->
                    PassiveWakeDecoder(model, 22050).use { decoder ->
                        val frame = ShortArray(441)
                        repeat(150) { assertNull(decoder.accept(frame, frame.size)) }
                        assertEquals(0L, decoder.inferenceFrames)
                        for (phrase in listOf("Atento Luna, dime la hora.", "Dime la hora.", "Atento Luna, dime la hora.")) {
                            val token = gate.arm(listOf(profile))
                            val results = mutableListOf<String>()
                            fun feed(count: Int) {
                                decoder.accept(frame, count)?.let { result ->
                                    val alternatives = result.optJSONArray("alternatives")
                                    if (alternatives == null) results += result.optString("text")
                                    else for (i in 0 until alternatives.length()) results += alternatives.getJSONObject(i).optString("text")
                                }
                            }
                            val pcm = engine.synthesize(profile, phrase)
                            try {
                                var offset = 0
                                while (offset < pcm.bytes.size) {
                                    val count = minOf(frame.size, (pcm.bytes.size - offset) / 2)
                                    repeat(count) { i -> frame[i] = ((pcm.bytes[offset + i * 2].toInt() and 255) or (pcm.bytes[offset + i * 2 + 1].toInt() shl 8)).toShort() }
                                    feed(count); offset += count * 2
                                }
                            } finally { pcm.bytes.fill(0) }
                            frame.fill(0); repeat(150) { feed(frame.size) }
                            val match = results.firstNotNullOfOrNull { gate.acceptUtterance(listOf(it), token) }
                            assertEquals("Fixture fija: $phrase → $results", phrase.startsWith("Atento"), match != null)
                            if (match != null) assertTrue(match.question.contains("hora"))
                        }
                        frame.fill(0)
                    }
                }
            }
        } finally { engine.close(); store.delete(); wake.delete() }
    }
}
