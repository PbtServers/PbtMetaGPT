package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer

/** Mismo decodificador para captura real y regresión de inferencia; un único hilo propietario. */
class LocalCommandDecoder(path: String, private val sampleRate: Float = 16000f) : AutoCloseable {
    private val model = Model(path)
    private var recognizer: Recognizer
    private var chat: Recognizer
    init {
        val open = try { create(false) } catch (problem: Throwable) { model.close(); throw problem }
        recognizer = open
        chat = try { create(true) } catch (problem: Throwable) { open.close(); model.close(); throw problem }
    }
    private fun create(restricted: Boolean): Recognizer {
        val value = if (restricted) Recognizer(model, sampleRate, JSONArray(ChatGptCommandGrammar.vocabulary).toString()) else Recognizer(model, sampleRate)
        try { value.setWords(true); value.setEndpointerMode(1); return value }
        catch (problem: Throwable) { value.close(); throw problem }
    }
    fun accept(buffer: ShortArray, size: Int): CommandHypothesis? {
        // Ambos reciben exactamente los mismos PCM y cierran el segmento juntos.
        // Solo el reconocedor libre decide el fin; la gramática no corta frases.
        chat.acceptWaveForm(buffer, size)
        if (!recognizer.acceptWaveForm(buffer, size)) return null
        return combine(recognizer.result, chat.result)
    }
    fun finish(): CommandHypothesis = combine(recognizer.finalResult, chat.finalResult)
    private fun combine(open: String, restricted: String): CommandHypothesis {
        val full = decode(open, average = false)
        val command = decode(restricted, average = true)
        return if (ChatGptCommandGrammar.accepts(full, command)) command else full
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
        try { recognizer.close() } finally { chat.close() }
        recognizer = nextOpen; chat = nextChat
    }
    override fun close() { try { recognizer.close() } finally { try { chat.close() } finally { model.close() } } }
}
