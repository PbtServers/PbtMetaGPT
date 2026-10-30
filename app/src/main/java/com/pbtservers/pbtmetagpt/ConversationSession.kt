package com.pbtservers.pbtmetagpt

import java.text.Normalizer
import java.util.Locale

enum class SessionPhase(val label: String) {
    IDLE("Listo"), WAKE_LISTENING("Esperando frase"), USER_LISTENING("Te escucho"),
    PROCESSING("Pensando"), SPEAKING("Hablando"), FOLLOW_UP_LISTENING("Conversación activa · te escucho"),
    INTERRUPTING("Parando"), SUSPENDED("Pausado"), STOPPED("Conversación finalizada"),
}

data class ListeningOptions(
    val continuous: Boolean = true,
    val background: Boolean = false,
    val interruption: Boolean = true,
    val recognizeStop: Boolean = true,
    val sensitivity: Float = 0.5f,
    val inactivitySeconds: Int = 0,
    val endPhrase: String = "termina la conversación",
    val goodbye: Boolean = false,
) {
    fun validated(): ListeningOptions {
        require(sensitivity.isFinite() && sensitivity in 0f..1f)
        require(inactivitySeconds == 0 || inactivitySeconds in 30..3600)
        require(endPhrase.length in 1..80 && !SecretPatterns.contains(endPhrase))
        val closing = VoiceCommands.normalize(endPhrase)
        require(closing.isNotBlank())
        require(VoiceCommands.parse(closing, "", ListeningOptions()).let { it == null || it == LocalVoiceCommand.END })
        return this
    }
}

enum class LocalVoiceCommand { INTERRUPT, END, STOP_LISTENING, CHATGPT, SYSTEM_ASSISTANT, MUSIC_PLAY, MUSIC_PAUSE, MUSIC_NEXT, MUSIC_PREVIOUS }

/** Solo enunciados completos: nunca una subcadena ni una hipótesis parcial. */
object VoiceCommands {
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9 ]"), " ").trim().replace(Regex("\\s+"), " ")
    private fun acousticText(text: String): String = normalize(text)
        .replace(Regex("\\bchatgpt\\b"), "chat ge pe te").replace(Regex("\\bgpt\\b"), "ge pe te")
    fun matchesEcho(heard: String, output: String): Boolean {
        val command = acousticText(heard)
        return command.isNotBlank() && " ${acousticText(output)} ".contains(" $command ")
    }
    fun parse(text: String, name: String, options: ListeningOptions): LocalVoiceCommand? {
        val normalized = normalize(text)
        val prefix = normalize(name)
        val command = if (prefix.isNotEmpty() && normalized.startsWith("$prefix ")) normalized.removePrefix("$prefix ") else normalized
        return when {
            command == "para" && options.recognizeStop -> LocalVoiceCommand.INTERRUPT
            command == "deja de escuchar" -> LocalVoiceCommand.STOP_LISTENING
            command in setOf("termina", "termina la conversacion", normalize(options.endPhrase)) -> LocalVoiceCommand.END
            command == "hasta luego" && options.goodbye -> LocalVoiceCommand.END
            command in setOf("abre gpt live", "inicia gpt live", "abre chatgpt", "inicia chatgpt en voz", "abre chatgpt en voz", "quiero hablar con chatgpt") || command in ChatGptCommandGrammar.phrases -> LocalVoiceCommand.CHATGPT
            command in setOf("abre google", "abre el asistente de google", "abre asistente google", "abre el asistente del telefono", "quiero hablar con google") -> LocalVoiceCommand.SYSTEM_ASSISTANT
            else -> MusicCommands.parse(command)?.let(MusicCommands::command)
        }
    }
}

/** Autoridad de sesión y turnos. Un resultado anterior no puede cambiar el estado actual. */
class ConversationSession {
    var phase = SessionPhase.IDLE; private set
    var epoch = 0L; private set
    var authorized = false; private set
    var spoken = false; private set
    fun start(spoken: Boolean) { epoch++; authorized = true; this.spoken = spoken; phase = SessionPhase.USER_LISTENING }
    fun enableSpeech() { if (authorized) spoken = true }
    fun move(value: SessionPhase, token: Long = epoch): Boolean {
        if (!authorized || token != epoch || phase == SessionPhase.SUSPENDED) return false
        phase = value; return true
    }
    fun accepts(token: Long) = authorized && token == epoch && phase != SessionPhase.SUSPENDED
    fun interrupt(): Long { epoch++; if (authorized) phase = SessionPhase.INTERRUPTING; return epoch }
    fun completed(token: Long, continuous: Boolean): Boolean {
        if (!accepts(token)) return false
        phase = if (spoken && continuous) SessionPhase.FOLLOW_UP_LISTENING else SessionPhase.IDLE
        return spoken && continuous
    }
    fun suspendSession() { epoch++; phase = SessionPhase.SUSPENDED }
    fun resume() { if (authorized) phase = SessionPhase.FOLLOW_UP_LISTENING }
    fun stop() { epoch++; authorized = false; spoken = false; phase = SessionPhase.STOPPED }
}

/** Contexto en RAM acotado; las instrucciones viajan por separado y nunca se recortan aquí. */
class ConversationHistory(private val maxMessages: Int = 32, private val maxCharacters: Int = 15000) {
    private val entries = mutableListOf<Pair<String, String>>()
    fun user(text: String) { entries += "user" to text; trim() }
    fun assistant(text: String) { entries += "assistant" to text; trim() }
    fun snapshot(): List<Pair<String, String>> = entries.toList()
    fun clear() = entries.clear()
    private fun trim() {
        while (entries.size > 1 && (entries.size > maxMessages || entries.sumOf { it.second.length } > maxCharacters)) {
            entries.removeAt(0)
            while (entries.firstOrNull()?.first == "assistant") entries.removeAt(0)
        }
    }
}

/** Confianza real del reconocedor, separación de eco y correspondencia exacta. */
class InterruptionGate {
    private var lastAccepted = Long.MIN_VALUE
    fun accept(text: String, confidence: Float, now: Long, protectedUntil: Long, echoSafe: Boolean,
        name: String, options: ListeningOptions): LocalVoiceCommand? {
        if (!options.interruption || !echoSafe || now < protectedUntil || !confidence.isFinite()) return null
        if (confidence < 0.95f - options.sensitivity * 0.25f) return null
        if (lastAccepted != Long.MIN_VALUE && now - lastAccepted < 700) return null
        val command = VoiceCommands.parse(text, name, options) ?: return null
        lastAccepted = now
        return command
    }
}
