package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer

/** Mismo decodificador para captura real y regresión de inferencia; un único hilo propietario. */
class LocalCommandDecoder(path: String, private val sampleRate: Float = 16000f,
    private val evaluation: ((CommandHypothesis, CommandHypothesis, SpeechBounds?) -> Unit)? = null) : AutoCloseable {
    private val model = Model(path)
    private var recognizer: Recognizer
    private var chat: Recognizer
    private var music: Recognizer
    private var vad = AdaptiveVad(sampleRate.toInt())
    private var samples = 0L
    private var voiceStart: Double? = null
    private var voiceEnd = 0.0
    init {
        val open = try { create(false) } catch (problem: Throwable) { model.close(); throw problem }
        recognizer = open
        chat = try { create(true) } catch (problem: Throwable) { open.close(); model.close(); throw problem }
        music = try { createMusic() } catch (problem: Throwable) { chat.close(); open.close(); model.close(); throw problem }
    }
    private fun createMusic(): Recognizer {
        val value = Recognizer(model, sampleRate, JSONArray((MusicCommandGrammar.vocabulary + SystemAssistantCommandGrammar.vocabulary).distinct()).toString())
        try { value.setWords(true); value.setEndpointerMode(1); return value }
        catch (problem: Throwable) { value.close(); throw problem }
    }
    private fun create(restricted: Boolean): Recognizer {
        val value = if (restricted) Recognizer(model, sampleRate, JSONArray(ChatGptCommandGrammar.vocabulary).toString()) else Recognizer(model, sampleRate)
        try { value.setWords(true); value.setEndpointerMode(1); return value }
        catch (problem: Throwable) { value.close(); throw problem }
    }
    fun accept(buffer: ShortArray, size: Int): CommandHypothesis? {
        samples += size
        when (vad.accept(buffer, size)) {
            AdaptiveVad.Event.START -> {
                voiceStart = (samples / sampleRate.toDouble() - .060).coerceAtLeast(0.0)
                voiceEnd = samples / sampleRate.toDouble()
            }
            AdaptiveVad.Event.VOICE -> voiceEnd = samples / sampleRate.toDouble()
            else -> Unit
        }
        // Ambos reciben exactamente los mismos PCM y cierran el segmento juntos.
        // Solo el reconocedor libre decide el fin; la gramática no corta frases.
        chat.acceptWaveForm(buffer, size)
        music.acceptWaveForm(buffer, size)
        if (!recognizer.acceptWaveForm(buffer, size)) return null
        return combine(recognizer.result, chat.result, music.result)
    }
    fun finish(): CommandHypothesis = combine(recognizer.finalResult, chat.finalResult, music.finalResult)
    private fun combine(open: String, restricted: String, musical: String): CommandHypothesis {
        val full = decode(open, average = false)
        val command = decode(restricted, average = true)
        val musicCommand = decode(musical, average = true)
        val bounds = voiceStart?.let { SpeechBounds(it, voiceEnd) }
        evaluation?.invoke(full, command, bounds)
        val result = when {
            bounds != null && ChatGptCommandGrammar.accepts(full, command, bounds) -> command
            bounds != null && SystemAssistantCommandGrammar.accepts(full, musicCommand, bounds) -> musicCommand
            bounds != null && MusicCommandGrammar.accepts(full, musicCommand, bounds) -> musicCommand
            else -> full
        }
        vad = AdaptiveVad(sampleRate.toInt()); voiceStart = null; voiceEnd = 0.0
        return result
    }
    private fun decode(json: String, average: Boolean): CommandHypothesis {
        val result = JSONObject(json)
        val words = result.optJSONArray("result")
        val confidences = if (words == null) emptyList() else (0 until words.length()).map { words.getJSONObject(it).optDouble("conf", 0.0) }
        val confidence = if (confidences.isEmpty()) 0f else if (average) confidences.average().toFloat() else confidences.min().toFloat()
        return CommandHypothesis(result.optString("text"), confidence,
            words?.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0,
            words?.optJSONObject(words.length() - 1)?.optDouble("end", 0.0) ?: 0.0)
    }
    fun reset() {
        // Reset() nativo conserva el reloj acumulado: recrear solo decodificadores
        // al perder tramas evita registrar latencias cero con un origen nuevo.
        val nextOpen = create(false)
        val nextChat = try { create(true) } catch (problem: Throwable) { nextOpen.close(); throw problem }
        val nextMusic = try { createMusic() } catch (problem: Throwable) { nextChat.close(); nextOpen.close(); throw problem }
        try { recognizer.close() } finally { try { chat.close() } finally { music.close() } }
        recognizer = nextOpen; chat = nextChat; music = nextMusic
        samples = 0; vad = AdaptiveVad(sampleRate.toInt()); voiceStart = null; voiceEnd = 0.0
    }
    override fun close() { try { recognizer.close() } finally { try { chat.close() } finally { try { music.close() } finally { model.close() } } } }
}
