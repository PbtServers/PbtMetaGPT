package com.pbtservers.pbtmetagpt

/** No inventa órdenes desde palabras parecidas: ambos decodificadores deben dar el mismo texto completo. */
object SystemAssistantCommandGrammar {
    val phrases = listOf("abre google", "abre el asistente de google", "abre asistente google", "abre el asistente del telefono", "quiero hablar con google")
    val vocabulary = phrases + phrases.map { "jarvis $it" }
    fun shouldRetry(open: CommandHypothesis, restricted: CommandHypothesis, speech: SpeechBounds): Boolean {
        val free = VoiceCommands.normalize(open.text).removePrefix("jarvis ")
        return open.confidence.isFinite() && open.confidence < .85f && free.startsWith("abre el asistente ") &&
            accepts(open.copy(text = restricted.text), restricted, speech)
    }
    fun acceptsAlternatives(open: CommandHypothesis, candidates: List<String>, restricted: CommandHypothesis, speech: SpeechBounds): Boolean {
        if (!shouldRetry(open, restricted, speech)) return false
        // Los scores N-best no son probabilidades. La confianza continúa siendo
        // la de las palabras de la hipótesis restringida y se exige acuerdo completo.
        return candidates.take(5).any { accepts(open.copy(text = it), restricted, speech) }
    }
    fun accepts(open: CommandHypothesis, restricted: CommandHypothesis, speech: SpeechBounds): Boolean {
        val target = VoiceCommands.normalize(restricted.text).removePrefix("jarvis ")
        if (target !in phrases || VoiceCommands.normalize(open.text).removePrefix("jarvis ") != target) return false
        if (!restricted.confidence.isFinite() || restricted.confidence < .95f) return false
        if (listOf(speech.onsetSeconds, speech.endSeconds, restricted.onsetSeconds, restricted.endSeconds).any { !it.isFinite() }) return false
        return speech.endSeconds > speech.onsetSeconds && kotlin.math.abs(speech.onsetSeconds - restricted.onsetSeconds) <= .12 &&
            kotlin.math.abs(speech.endSeconds - restricted.endSeconds) <= .18
    }
}
