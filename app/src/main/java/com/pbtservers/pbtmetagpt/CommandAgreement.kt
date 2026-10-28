package com.pbtservers.pbtmetagpt

import kotlin.math.abs

data class CommandHypothesis(val text: String, val confidence: Float, val onsetSeconds: Double, val endSeconds: Double = onsetSeconds)
data class SpeechBounds(val onsetSeconds: Double, val endSeconds: Double)

/** El léxico es-0.42 contiene ge/pe/te, pero no GPT. «Para» exige además coincidencia libre exacta y VAD. */
object ChatGptCommandGrammar {
    val phrases = listOf("abre ge pe te live", "inicia ge pe te live", "abre chat ge pe te en voz", "quiero hablar con chat ge pe te")
    val vocabulary = phrases + phrases.map { "jarvis $it" } + listOf("para", "[unk]")
    fun accepts(open: CommandHypothesis, restricted: CommandHypothesis,
        speech: SpeechBounds = SpeechBounds(open.onsetSeconds, open.endSeconds)): Boolean {
        val text = VoiceCommands.normalize(restricted.text)
        val stop = text == "para"
        if (stop && VoiceCommands.normalize(open.text) != "para") return false
        if (!stop && text !in phrases && text.removePrefix("jarvis ") !in phrases) return false
        if (!restricted.confidence.isFinite() || (!stop && restricted.confidence < .9f) || open.text.isBlank()) return false
        if (listOf(speech.onsetSeconds, speech.endSeconds, restricted.onsetSeconds, restricted.endSeconds).any { !it.isFinite() }) return false
        if (speech.endSeconds <= speech.onsetSeconds) return false
        // Una gramática puede omitir «no» o «explícame cómo». Exigir el mismo
        // inicio y final que la voz detectada rechaza órdenes incrustadas/negadas.
        if (abs(speech.onsetSeconds - restricted.onsetSeconds) > .12 || abs(speech.endSeconds - restricted.endSeconds) > .18) return false
        return VoiceCommands.normalize(open.text).substringBefore(' ') !in setOf("no", "nunca", "explica", "explicame", "como", "si")
    }
}
