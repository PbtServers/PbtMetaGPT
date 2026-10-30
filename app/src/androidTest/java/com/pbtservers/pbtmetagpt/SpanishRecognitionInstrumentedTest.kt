package com.pbtservers.pbtmetagpt

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.vosk.Recognizer

/** Corpus sintético público es-ES. No acredita voz humana, Bluetooth, Whisper ni sonido físico. */
class SpanishRecognitionInstrumentedTest {
    @Test fun comparaDictadoLibreYOrdenesA16kYCuantificaOmisionesSinInventarExactitud() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val app = context.applicationContext as PbtApplication
        val wake = WakeModel(context)
        val store = SpainVoiceModel(context)
        val voice = SpainVoiceEngine(context, reproducibleFixture = true)
        val profile = VoiceModes.spain(AssistantProfile(name = "Jarvis"), "davefx").validated()
        val cases = listOf("Pon música.", "Ponme algo en Spotify.", "Abre Google.", "Busca en Google.", "Abre ChatGPT.",
            "¿Qué tiempo hará mañana en Barcelona?", "¿Cuánto tardaré en llegar a Esterri d'Àneu?", "Quiero ir a Puigcerdà.",
            "Para.", "Termina.", "¿Qué es Spotify?", "¿Cómo funciona Google?", "¿Qué música me recomiendas?", "No pongas Spotify.", "Atento Jarvis.", "Pon música en Spotify.", "Quiero saber qué tiempo va a hacer mañana.",
            "No, espera, que todavía no he terminado.", "¿Qué temperatura hará en Esterri d'Àneu?", "¿Cuánto tardaré en llegar a Puigcerdà?")
        try {
            wake.install { instrumentation.context.assets.open("activacion-prueba.zip") }
            store.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            withContext(Dispatchers.IO) {
                app.voskModels.close()
                val firstAt = SystemClock.elapsedRealtime()
                val first = app.voskModels.acquire(wake.model.absolutePath)
                val firstMs = SystemClock.elapsedRealtime() - firstAt
                first.use { cached ->
                    val reuseAt = SystemClock.elapsedRealtime()
                    app.voskModels.acquire(wake.model.absolutePath).use { assertSame(cached.model, it.model) }
                    val reuseMs = SystemClock.elapsedRealtime() - reuseAt
                    var freeTotal = RecognitionErrors(0, 0, 0, 0)
                    var commandTotal = RecognitionErrors(0, 0, 0, 0)
                    var correctIntents = 0
                    for ((index, phrase) in cases.withIndex()) {
                        val pcm = voice.synthesize(profile, phrase)
                        val samples = at16k(pcm)
                        pcm.bytes.fill(0)
                        val full = mutableListOf<String>()
                        val commands = mutableListOf<String>()
                        var dictation = CommandHypothesis("", 0f, 0.0)
                        val started = SystemClock.elapsedRealtime()
                        var localCommandMs: Long? = null
                        try {
                            if (phrase in listOf("Para.", "Termina.", "Pon música.") || phrase.startsWith("¿Qué") || phrase.startsWith("¿Cómo") || phrase.startsWith("No ")) {
                                // Misma ruta previa a Whisper: no red, ni segundo capturador.
                                val bytes = ByteArray(samples.size * 2)
                                repeat(samples.size) { index -> bytes[index * 2] = samples[index].toByte(); bytes[index * 2 + 1] = (samples[index].toInt() shr 8).toByte() }
                                val wav = WavAudio.encodePcm16Mono(16000, bytes)
                                try {
                                    val local = LocalRecordedCommand(context).recognize(wav, "Jarvis", ListeningOptions())
                                    if (phrase in listOf("Para.", "Termina.", "Pon música.")) assertNotNull("Orden local antes de Whisper: $phrase", local)
                                    else assertNull("Pregunta/negación no debe sustituirse por una orden: $phrase", local)
                                } finally { bytes.fill(0); wav.fill(0); localCommandMs = SystemClock.elapsedRealtime() - started }
                            }
                            val decodingAt = SystemClock.elapsedRealtime()
                            Recognizer(cached.model, 16000f).use { free ->
                                VoskDictationDecoder(cached.model).use { dictationDecoder ->
                                LocalCommandDecoder(wake.model.absolutePath, sharedModel = cached.model).use { decoder ->
                                    fun feed(frame: ShortArray, count: Int) {
                                        dictationDecoder.accept(frame, count)
                                        if (free.acceptWaveForm(frame, count)) JSONObject(free.result).optString("text").takeIf { it.isNotBlank() }?.let(full::add)
                                        decoder.accept(frame, count)?.text?.takeIf { it.isNotBlank() }?.let(commands::add)
                                    }
                                    val frame = ShortArray(320)
                                    repeat(25) { feed(frame, frame.size) }
                                    var offset = 0
                                    while (offset < samples.size) {
                                        val count = minOf(frame.size, samples.size - offset)
                                        samples.copyInto(frame, 0, offset, offset + count)
                                        feed(frame, count); offset += count
                                    }
                                    frame.fill(0); repeat(150) { feed(frame, frame.size) }
                                    JSONObject(free.finalResult).optString("text").takeIf { it.isNotBlank() }?.let(full::add)
                                    decoder.finish().text.takeIf { it.isNotBlank() }?.let(commands::add)
                                    dictation = dictationDecoder.finish()
                                }
                                }
                            }
                            val recognized = commands.joinToString(" ")
                            val before = RecognitionMetrics.words(phrase, full.joinToString(" "))
                            val after = RecognitionMetrics.words(phrase, recognized)
                            fun intent(text: String): String? = VoiceCommands.parse(text, "Jarvis", ListeningOptions())?.name ?: WebCommands.parse(text)?.destination?.name
                            val expectedIntent = intent(phrase)
                            val recognizedIntent = intent(recognized)
                            if (expectedIntent == recognizedIntent) correctIntents++
                            freeTotal += before; commandTotal += after
                            val score = JSONObject().put("caso", index).put("origen", "Davefx sintético es-ES").put("hz", 16000)
                                .put("libre_wer", before.rate).put("comandos_wer", after.rate)
                                .put("dictado_produccion_wer", RecognitionMetrics.words(phrase, dictation.text).rate)
                                .put("dictado_produccion_cer", RecognitionMetrics.characters(phrase, dictation.text).rate)
                                .put("dictado_omisiones", RecognitionMetrics.words(phrase, dictation.text).deletions)
                                .put("dictado_produccion_wer", RecognitionMetrics.words(phrase, dictation.text).rate)
                                .put("dictado_produccion_cer", RecognitionMetrics.characters(phrase, dictation.text).rate)
                                .put("dictado_omisiones", RecognitionMetrics.words(phrase, dictation.text).deletions)
                                .put("comandos_cer", RecognitionMetrics.characters(phrase, recognized).rate)
                                .put("intencion_esperada", expectedIntent).put("intencion_reconocida", recognizedIntent)
                                .put("omisiones", after.deletions).put("inferencia_ms", SystemClock.elapsedRealtime() - decodingAt).put("orden_local_ms", localCommandMs)
                            // Solo la fixture pública usa textos en informes de tests. La app no los registra.
                            Log.i("PbtAsrTest", score.toString())
                            if (expectedIntent == null)
                                assertNull("Una pregunta/negación sintética no debe ejecutar: $phrase → $recognized", VoiceCommands.parse(recognized, "Jarvis", ListeningOptions()))
                        } finally { samples.fill(0); full.clear(); commands.clear() }
                    }
                    assertTrue(freeTotal.reference > 20)
                    assertNotNull(commandTotal.rate)
                    Log.i("PbtAsrTest", JSONObject().put("corpus", "sintético es-ES, " + cases.size + " frases, 16 kHz")
                        .put("libre_wer", freeTotal.rate).put("comandos_wer", commandTotal.rate).put("omisiones", commandTotal.deletions)
                        .put("modelo_primera_carga_ms", firstMs).put("modelo_reutilizado_ms", reuseMs)
                        .put("intenciones_correctas", correctIntents).put("intenciones_total", cases.size)
                        .put("whisper_real_ejecutado", false).put("android_dictado_real_ejecutado", false).toString())
                }
            }
        } finally { voice.close(); app.voskModels.close(); store.delete(); wake.delete() }
    }
    private fun at16k(audio: PcmAudio): ShortArray {
        val inputCount = audio.bytes.size / 2
        fun sample(index: Int): Int = ((audio.bytes[index * 2].toInt() and 255) or (audio.bytes[index * 2 + 1].toInt() shl 8)).toShort().toInt()
        return ShortArray((inputCount.toLong() * 16000 / audio.sampleRate).toInt()) { index ->
            val position = index.toDouble() * audio.sampleRate / 16000
            val left = position.toInt().coerceAtMost(inputCount - 1)
            val right = (left + 1).coerceAtMost(inputCount - 1)
            (sample(left) + (sample(right) - sample(left)) * (position - left)).toInt().toShort()
        }
    }
}
