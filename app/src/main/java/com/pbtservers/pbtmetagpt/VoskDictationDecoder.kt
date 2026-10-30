package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer

/** Dictado libre: el VAD de captura decide el final, no una gramática de órdenes. */
class VoskDictationDecoder(model: Model) : AutoCloseable {
    private val recognizer = Recognizer(model, 16000f).apply { setWords(true) }
    private val results = mutableListOf<CommandHypothesis>()
    fun accept(samples: ShortArray, count: Int) {
        if (recognizer.acceptWaveForm(samples, count)) collect(recognizer.result)
    }
    private fun collect(json: String) {
        val value = JSONObject(json)
        val text = value.optString("text").trim()
        if (text.isBlank()) return
        val words = value.optJSONArray("result")
        val confidence = if (words == null || words.length() == 0) 0f else
            (0 until words.length()).map { words.getJSONObject(it).optDouble("conf", 0.0) }.min().toFloat()
        results += CommandHypothesis(text, confidence, 0.0)
    }
    fun finish(): CommandHypothesis {
        collect(recognizer.finalResult)
        return CommandHypothesis(results.joinToString(" ") { it.text }, results.minOfOrNull { it.confidence } ?: 0f, 0.0)
    }
    override fun close() { results.clear(); recognizer.close() }
}
