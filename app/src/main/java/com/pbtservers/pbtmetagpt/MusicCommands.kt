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
