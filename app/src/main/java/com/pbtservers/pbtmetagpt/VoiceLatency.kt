package com.pbtservers.pbtmetagpt

/** Solo duraciones numéricas. Nunca contiene texto, URLs, modelos, claves o audio. */
enum class VoiceStage { MICROPHONE_READY, SPEECH_START, FIRST_SENTENCE, TRANSCRIPTION, END_OF_SPEECH, GROQ_FIRST_TOKEN, GROQ_COMPLETE, TTS_CONNECT, TTS_SUBMIT, TTS_WAIT_COMBINED, TTS_DOWNLOAD, TTS_SYNTHESIS, TTS_FIRST_PCM, RVC_CONVERSION, FIRST_AUDIO_FRAME, WAKE_DETECTION, STOP_DETECTION, STOP_AUDIO_REQUEST, FOLLOW_UP_READY, TOTAL }
class VoiceLatency(private val clock: () -> Long = { System.nanoTime() / 1000000 }) {
    private val started = clock()
    private var responseStart = started
    private val values = linkedMapOf<VoiceStage, Long>()
    @Synchronized fun responseStarted() { responseStart = clock() }
    @Synchronized fun mark(stage: VoiceStage) { values.putIfAbsent(stage, (clock() - if (stage == VoiceStage.FIRST_AUDIO_FRAME) responseStart else started).coerceAtLeast(0)) }
    @Synchronized fun once(stage: VoiceStage, duration: Long) { values.putIfAbsent(stage, duration.coerceAtLeast(0)) }
    @Synchronized fun add(stage: VoiceStage, duration: Long) { values[stage] = (values[stage] ?: 0) + duration.coerceAtLeast(0) }
    @Synchronized fun snapshot(): Map<VoiceStage, Long> = values.toMap()
    fun now() = clock()
}

/** Fronteras de frases, sin cortar decimales, abreviaturas frecuentes ni palabras. */
class SpeechSegments(private val remote: Boolean, private val groupedLocal: Boolean = false) {
    private val pending = StringBuilder()
    private var emitted = 0
    fun append(delta: String): List<String> {
        pending.append(delta)
        val ready = mutableListOf<String>()
        while (!(remote && emitted >= 1)) {
        val minimum = if (remote || groupedLocal) 80 else 24
        val boundary = pending.indices.firstOrNull { i ->
            i + 1 >= minimum && pending[i] in ".!?;:\n" &&
                !(pending[i] == '.' && i > 0 && pending[i - 1].isDigit() &&
                    (i == pending.lastIndex || pending[i + 1].isDigit())) &&
                !pending.substring(0, i + 1).trimEnd().endsWithAbbreviation() &&
                (i == pending.lastIndex || pending[i + 1].isWhitespace())
        } ?: if (!remote && pending.length > 260) (minOf(240, pending.lastIndex) downTo 80).firstOrNull { pending[it].isWhitespace() } else null
        if (boundary == null) break
        val phrase = pending.substring(0, boundary + 1).trim()
        pending.delete(0, boundary + 1)
        emitted++
        ready += phrase
        }
        return ready
    }
    fun finish(): String? = pending.toString().trim().takeIf { it.isNotEmpty() }.also { pending.clear() }
    private fun String.endsWithAbbreviation() = listOf("Sr.", "Sra.", "Dr.", "Dra.", "Ud.", "p. ej.", "etc.").any { endsWith(it, ignoreCase = true) }
}
