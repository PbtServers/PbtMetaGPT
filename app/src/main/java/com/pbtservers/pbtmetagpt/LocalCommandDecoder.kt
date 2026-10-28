package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

data class CommandHypothesis(val text: String, val confidence: Float, val onsetSeconds: Double)

/** Mismo decodificador para captura real y regresión de inferencia; un único hilo propietario. */
class LocalCommandDecoder(path: String, sampleRate: Float = 16000f) : AutoCloseable {
    private val model = Model(path)
    private val recognizer = try { Recognizer(model, sampleRate).apply { setWords(true); setEndpointerMode(1) } }
        catch (problem: Throwable) { model.close(); throw problem }
    fun accept(buffer: ShortArray, size: Int): CommandHypothesis? {
        if (!recognizer.acceptWaveForm(buffer, size)) return null
        val result = JSONObject(recognizer.result)
        val words = result.optJSONArray("result")
        val confidence = if (words == null || words.length() == 0) 0f else (0 until words.length())
            .minOf { words.getJSONObject(it).optDouble("conf", 0.0) }.toFloat()
        return CommandHypothesis(result.optString("text"), confidence,
            words?.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0)
    }
    fun reset() = recognizer.reset()
    override fun close() { try { recognizer.close() } finally { model.close() } }
}
