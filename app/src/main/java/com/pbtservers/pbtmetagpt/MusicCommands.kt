package com.pbtservers.pbtmetagpt

enum class MusicAction { PLAY, PAUSE, NEXT, PREVIOUS }

/** Órdenes completas; nunca ejecutar una mención, una negación ni una subcadena. */
object MusicCommands {
    val phrases: Map<String, MusicAction> = buildMap {
        listOf("pon spotify", "reproduce spotify", "pon musica", "reproduce musica", "reanuda la musica", "dale al play",
            "ponme musica", "pon algo de musica", "ponme algo de musica", "quiero escuchar musica", "abre spotify", "spotify",
            "ponme algo en spotify", "reproduce musica en spotify", "continua la musica", "continua musica", "continua spotify").forEach { put(it, MusicAction.PLAY) }
        listOf("pausa spotify", "pausa la musica", "pausa musica", "para spotify").forEach { put(it, MusicAction.PAUSE) }
        listOf("siguiente cancion", "cambia de cancion", "pasa de cancion", "pon la siguiente").forEach { put(it, MusicAction.NEXT) }
        listOf("cancion anterior", "anterior cancion", "vuelve a la cancion anterior").forEach { put(it, MusicAction.PREVIOUS) }
    }
    fun parse(text: String): MusicAction? = phrases[VoiceCommands.normalize(text)]
    fun action(command: LocalVoiceCommand): MusicAction? = when (command) {
        LocalVoiceCommand.MUSIC_PLAY -> MusicAction.PLAY
        LocalVoiceCommand.MUSIC_PAUSE -> MusicAction.PAUSE
        LocalVoiceCommand.MUSIC_NEXT -> MusicAction.NEXT
        LocalVoiceCommand.MUSIC_PREVIOUS -> MusicAction.PREVIOUS
        else -> null
    }
    fun command(action: MusicAction): LocalVoiceCommand = when (action) {
        MusicAction.PLAY -> LocalVoiceCommand.MUSIC_PLAY
        MusicAction.PAUSE -> LocalVoiceCommand.MUSIC_PAUSE
        MusicAction.NEXT -> LocalVoiceCommand.MUSIC_NEXT
        MusicAction.PREVIOUS -> LocalVoiceCommand.MUSIC_PREVIOUS
    }
}

/** Gramática de acciones musicales: misma intervención PCM y fronteras VAD que el dictado libre. */
object MusicCommandGrammar {
    val phrases = MusicCommands.phrases.keys.toList()
    val vocabulary = phrases + phrases.map { "jarvis $it" } + listOf("[unk]")
    fun accepts(open: CommandHypothesis, restricted: CommandHypothesis, speech: SpeechBounds): Boolean {
        val text = VoiceCommands.normalize(restricted.text).removePrefix("jarvis ")
        if (text !in phrases || open.text.isBlank() || !restricted.confidence.isFinite() || restricted.confidence < .93f) return false
        val free = VoiceCommands.normalize(open.text).removePrefix("jarvis ")
        if (text == "spotify" && free != "spotify") return false
        if (free.startsWith("quiero ") && "escuchar" !in free.split(' ')) return false
        if (listOf(speech.onsetSeconds, speech.endSeconds, restricted.onsetSeconds, restricted.endSeconds).any { !it.isFinite() }) return false
        if (speech.endSeconds <= speech.onsetSeconds || kotlin.math.abs(speech.onsetSeconds - restricted.onsetSeconds) > .12 ||
            kotlin.math.abs(speech.endSeconds - restricted.endSeconds) > .18) return false
        return free.substringBefore(' ') !in setOf("no", "nunca", "explica", "explicame", "como", "que", "cual", "si")
    }
}
