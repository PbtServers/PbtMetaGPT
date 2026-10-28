package com.pbtservers.pbtmetagpt

import kotlin.math.abs

data class CommandHypothesis(val text: String, val confidence: Float, val onsetSeconds: Double, val endSeconds: Double = onsetSeconds)

/** El léxico es-0.42 contiene ge/pe/te, pero no GPT. Nunca se fuerza «Para» con esta gramática. */
object ChatGptCommandGrammar {
    val phrases = listOf("abre ge pe te live", "inicia ge pe te live", "abre chat ge pe te en voz", "quiero hablar con chat ge pe te")
    val vocabulary = phrases + phrases.map { "jarvis $it" } + "[unk]"
    fun accepts(open: CommandHypothesis, restricted: CommandHypothesis): Boolean {
        val text = VoiceCommands.normalize(restricted.text)
        if (text !in phrases && text.removePrefix("jarvis ") !in phrases) return false
        if (!restricted.confidence.isFinite() || restricted.confidence < .9f || open.text.isBlank()) return false
        if (listOf(open.onsetSeconds, open.endSeconds, restricted.onsetSeconds, restricted.endSeconds).any { !it.isFinite() }) return false
        // Una gramática puede omitir «no» o «explícame cómo». Exigir el mismo
        // inicio y final que el dictado libre rechaza órdenes incrustadas/negadas.
        if (abs(open.onsetSeconds - restricted.onsetSeconds) > .12 || abs(open.endSeconds - restricted.endSeconds) > .45) return false
        return VoiceCommands.normalize(open.text).substringBefore(' ') !in setOf("no", "nunca", "explica", "explicame", "como", "si")
    }
}
