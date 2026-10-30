package com.pbtservers.pbtmetagpt

/** No convierte órdenes incompletas/dudosas en una pregunta para el proveedor. */
class RepeatLocalCommand(val explanation: String = "No he entendido la orden con seguridad. Repítela, por favor.") : Exception()

class LocalTurnRouter(private val command: (LocalVoiceCommand) -> Unit, private val web: (WebAction) -> Unit) {
    fun dispatch(text: String, name: String, options: ListeningOptions, confidence: Float? = null): Boolean {
        val action = VoiceCommands.parse(text, name, options)
        val destination = WebCommands.parse(text, name)
        if (action != null || destination != null) {
            if (confidence != null && !VoiceCommands.confident(text, confidence, name, options)) throw RepeatLocalCommand()
            if (destination != null) web(destination) else command(requireNotNull(action))
            return true
        }
        var normalized = VoiceCommands.normalize(text)
        val prefix = VoiceCommands.normalize(name)
        if (prefix.isNotBlank()) normalized = normalized.removePrefix("$prefix ")
        // Solo verbos imperativos dirigidos a destinos conocidos. Preguntas y
        // negaciones conservan el dictado libre; no basta mencionar una aplicación.
        if (normalized.substringBefore(' ') in setOf("pon", "ponme", "reproduce", "pausa", "continua", "reanuda", "abre") &&
            normalized.split(' ').any { it in setOf("musica", "spotify", "google", "chatgpt", "navegador") }) throw RepeatLocalCommand()
        return false
    }
}
