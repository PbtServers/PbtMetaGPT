package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoicePipelineTest {
    @Test fun primeraFraseSeReproduceAntesDeTerminarElTextoYPreservaOrden() = runBlocking {
        val first = CompletableDeferred<Unit>()
        val heard = mutableListOf<String>()
        var complete = false
        val phrases = listOf("Primera frase bastante larga.", " Segunda frase también completa.")
        withTimeout(2000) {
            StreamSpeechPipeline().run(false, produce = { emit ->
                emit(phrases[0]); first.await(); assertFalse(complete)
                emit(phrases[1]); complete = true; phrases.joinToString("")
            }, text = {}, synthesize = { PcmAudio(it.toByteArray(), 24000) }, play = { chunks ->
                for (audio in chunks) { heard += String(audio.bytes); first.complete(Unit) }
            }, failed = { throw it })
        }
        assertEquals(phrases.map { it.trim() }, heard)
    }
    @Test fun errorDeVozNoInterrumpeElTextoNiReintenta() = runBlocking {
        var attempts = 0; var errors = 0; val visible = StringBuilder()
        val full = "Una frase suficientemente larga. Otra frase que debe seguir siendo visible."
        val result = StreamSpeechPipeline().run(false, { emit -> full.chunked(5).forEach { emit(it) }; full },
            { visible.append(it) }, { attempts++; error("fallo") }, { chunks -> for (ignored in chunks) {} }, { errors++ })
        assertEquals(full, result); assertEquals(full, visible.toString()); assertEquals(1, attempts); assertEquals(1, errors)
    }
    @Test fun cancelacionDetieneSintesisYNoReproduceAudioTardio() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var stopped = false; var played = false
        val job = launch {
            StreamSpeechPipeline().run(false, { emit -> emit("Frase suficientemente larga para voz."); awaitCancellation() }, {}, {
                entered.complete(Unit); try { awaitCancellation() } finally { stopped = true }
            }, { for (ignored in it) played = true }, { throw it })
        }
        withTimeout(2000) { entered.await() }; job.cancelAndJoin()
        assertTrue(stopped); assertFalse(played)
    }
    @Test fun qwenNuncaSuperaDosSolicitudesYPreservaTodoElTexto() {
        val text = (1..8).joinToString(" ") { "Esta es una frase completa suficientemente larga número $it." }
        val segmenter = SpeechSegments(true)
        val output = text.chunked(7).flatMap { segmenter.append(it) } + listOfNotNull(segmenter.finish())
        assertTrue(output.size <= 2)
        assertEquals(text, output.joinToString(" "))
    }
    @Test fun rvcConservaStreamingYAcotaTodoElTextoParaLosMotoresLocales() = runBlocking<Unit> {
        val sentences = (1..25).map { "Esta respuesta larga conserva todas las palabras de la frase número $it." }
        val full = sentences.joinToString(" ")
        val first = CompletableDeferred<Unit>()
        val heard = mutableListOf<String>()
        var complete = false
        withTimeout(2000) {
            val result = StreamSpeechPipeline().run(remote = false, groupedLocal = true,
                produce = { emit ->
                    emit(sentences.take(2).joinToString(" ") + " ")
                    first.await(); assertFalse(complete)
                    emit(sentences.drop(2).joinToString(" "))
                    complete = true; full
                }, text = {}, synthesize = { phrase ->
                    require(phrase.length <= 1000) { "Límite real de entrada Kokoro/Piper" }
                    PcmAudio(phrase.toByteArray(), 24000)
                }, play = { audio ->
                    for (chunk in audio) { heard += String(chunk.bytes); first.complete(Unit) }
                }, failed = { throw it })
            assertEquals(full, result)
        }
        assertEquals(full, heard.joinToString(" "))
    }
    @Test fun noParteDecimalesNiAbreviaturas() {
        val segmenter = SpeechSegments(false)
        assertTrue(segmenter.append("El valor exacto para el Dr.").isEmpty())
        assertTrue(segmenter.append(" López es 3.").isEmpty())
        val result = segmenter.append("14 euros. Todo está correcto y terminado.") + listOfNotNull(segmenter.finish())
        assertTrue(result.first().contains("3.14")); assertTrue(result.first().contains("Dr. López"))
        val numberEnding = SpeechSegments(false).append("El resultado comprobado es 42. Ahora se conserva la pausa.")
        assertTrue(numberEnding.first().endsWith("42."))
    }
    @Test fun metricasSoloNumericasYPrimeraMarcaNoSeSobrescribe() {
        var now = 100L; val trace = VoiceLatency { now }
        now = 150; trace.mark(VoiceStage.GROQ_FIRST_TOKEN)
        now = 180; trace.mark(VoiceStage.GROQ_FIRST_TOKEN)
        trace.add(VoiceStage.TTS_DOWNLOAD, 20); trace.add(VoiceStage.TTS_DOWNLOAD, 10)
        assertEquals(50L, trace.snapshot()[VoiceStage.GROQ_FIRST_TOKEN])
        assertEquals(30L, trace.snapshot()[VoiceStage.TTS_DOWNLOAD])
        assertFalse(trace.snapshot().containsKey(VoiceStage.FIRST_AUDIO_FRAME))
    }
}
