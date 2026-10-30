package com.pbtservers.pbtmetagpt

enum class MusicAction { PLAY, PAUSE, NEXT, PREVIOUS }

/** Órdenes completas; nunca ejecutar una mención, una negación ni una subcadena. */
object MusicCommands {
    fun parse(text: String): MusicAction? = when (VoiceCommands.normalize(text)) {
        "pon spotify", "reproduce spotify", "pon musica", "reproduce musica", "reanuda la musica", "dale al play" -> MusicAction.PLAY
        "pausa spotify", "pausa la musica", "pausa musica" -> MusicAction.PAUSE
        "siguiente cancion", "cambia de cancion", "pasa de cancion" -> MusicAction.NEXT
        "cancion anterior", "anterior cancion", "vuelve a la cancion anterior" -> MusicAction.PREVIOUS
        else -> null
    }
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
    val phrases = listOf("pon spotify", "reproduce spotify", "pon musica", "reproduce musica", "reanuda la musica", "dale al play",
        "pausa spotify", "pausa la musica", "pausa musica", "siguiente cancion", "cambia de cancion", "pasa de cancion",
        "cancion anterior", "anterior cancion", "vuelve a la cancion anterior")
    val vocabulary = phrases + phrases.map { "jarvis $it" } + listOf("[unk]")
    fun accepts(open: CommandHypothesis, restricted: CommandHypothesis, speech: SpeechBounds): Boolean {
        val text = VoiceCommands.normalize(restricted.text).removePrefix("jarvis ")
        if (text !in phrases || open.text.isBlank() || !restricted.confidence.isFinite() || restricted.confidence < .93f) return false
        if (listOf(speech.onsetSeconds, speech.endSeconds, restricted.onsetSeconds, restricted.endSeconds).any { !it.isFinite() }) return false
        if (speech.endSeconds <= speech.onsetSeconds || kotlin.math.abs(speech.onsetSeconds - restricted.onsetSeconds) > .12 ||
            kotlin.math.abs(speech.endSeconds - restricted.endSeconds) > .18) return false
        return VoiceCommands.normalize(open.text).substringBefore(' ') !in setOf("no", "nunca", "explica", "explicame", "como", "si")
    }
}
