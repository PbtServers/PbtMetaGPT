package com.pbtservers.pbtmetagpt

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.UUID

data class AssistantProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val userName: String = "",
    val description: String = "",
    val personality: String = "",
    val tone: String = "Natural y claro",
    val formal: Boolean = false,
    val humor: String = "Sutil",
    val vocabulary: String = "",
    val instructions: String = "",
    val voice: String = "marin",
    val language: String = "es-ES",
    val detailed: Boolean = false,
    val speed: Double = 1.0,
    val activationPhrases: List<ActivationPhrase> = listOf(ActivationPhrase(id = "principal", primary = true)),
    val tolerantActivation: Boolean = false,
    val avatar: String = "",
    val confirmationSound: Boolean = false,
    val welcomeMessage: String = "",
    val sessionMinutes: Int = 10,
    val ttsEngine: String = "realtime",
    val ttsVoice: String = "",
    val ttsPackage: String = "",
    val pitch: Double = 1.0,
) {
    val phrase get() = (activationPhrases.firstOrNull { it.primary && it.enabled } ?: activationPhrases.firstOrNull { it.enabled })?.rendered(name) ?: "Sin frases activas"
    fun validated(): AssistantProfile {
        require(name.trim().length in 2..40 && name.trim().any(Char::isLetter) && name.all { it.isLetterOrDigit() || it == ' ' || it == '-' }) { "El nombre debe tener entre 2 y 40 letras, números, espacios o guiones." }
        require(listOf(userName, description, personality, tone, humor, vocabulary, instructions).all { it.length <= 4000 }) { "Cada campo admite hasta 4000 caracteres." }
        require(listOf(name, userName, description, personality, tone, humor, vocabulary, instructions).none { Regex("sk-[A-Za-z0-9_-]{20,}").containsMatchIn(it) }) { "No guardes claves API en los perfiles ni en sus instrucciones." }
        require(voice in (if (ttsEngine == "openai_tts") RealtimeSettings.speechVoices else RealtimeSettings.voices) && speed.isFinite() && speed in 0.25..1.5) { "Voz o velocidad no compatible con el motor elegido." }
        require(language.matches(Regex("[a-z]{2,3}(-[A-Za-z]{2,8})?"))) { "Usa un idioma como es-ES." }
        require(id.length in 1..80) { "Identificador de perfil no válido." }
        require(activationPhrases.map { it.id }.distinct().size == activationPhrases.size && activationPhrases.count { it.primary } <= 1) { "Las frases deben tener identificadores únicos y una sola principal." }
        activationPhrases.forEach { it.validate(name) }
        require(avatar.length <= 16 && welcomeMessage.length <= 1000 && !SecretPatterns.contains(welcomeMessage)) { "Avatar o bienvenida no válidos; no incluyas secretos." }
        require(sessionMinutes in 1..60) { "La sesión admite entre 1 y 60 minutos para limitar consumo y recursos." }
        require(ttsEngine in setOf("realtime", "android", "openai_tts", "external", "sherpa_vits")) { "Motor de voz no compatible." }
        require(ttsVoice.length <= 200 && ttsPackage.length <= 200 && !SecretPatterns.contains(ttsVoice + ttsPackage) && pitch.isFinite() && pitch in 0.5..2.0) { "Ajustes de síntesis no válidos." }
        if (ttsEngine in setOf("android", "external", "sherpa_vits")) require(ttsVoice.isNotBlank()) { "Selecciona una voz o modelo para el motor elegido antes de guardar." }
        if (ttsEngine == "android") require(ttsPackage.matches(Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+"))) { "Selecciona un motor Android instalado." }
        if (ttsEngine == "sherpa_vits") require(ttsVoice.matches(Regex("[a-f0-9-]{36}"))) { "Selecciona un paquete local instalado." }
        return copy(name = name.trim())
    }
    fun prompt(): String = """
        Eres un asistente llamado $name. Idioma: $language. ${if (language == "es-ES") "Utiliza español de España." else ""}
        ${if (userName.isBlank()) "" else "Dirígete al usuario como $userName."}
        Personalidad: $personality. Descripción: $description.
        Forma de hablar: $tone. Tratamiento: ${if (formal) "formal, de usted" else "informal, de tú"}.
        Humor: $humor. Vocabulario: $vocabulary.
        Respuestas habitualmente ${if (detailed) "detalladas y estructuradas" else "breves y claras"}.
        Instrucciones adicionales: $instructions
        Los personajes son inspiración de estilo, no una identidad ni una imitación exacta de voces originales.
    """.trimIndent()

    fun json(): JSONObject = JSONObject().put("id", id).put("name", name).put("userName", userName)
        .put("description", description).put("personality", personality).put("tone", tone).put("formal", formal)
        .put("humor", humor).put("vocabulary", vocabulary).put("instructions", instructions).put("voice", voice)
        .put("language", language).put("detailed", detailed).put("speed", speed)
        .put("activationPhrases", JSONArray().apply { activationPhrases.forEach { put(it.json()) } })
        .put("tolerantActivation", tolerantActivation).put("avatar", avatar).put("confirmationSound", confirmationSound)
        .put("welcomeMessage", welcomeMessage).put("sessionMinutes", sessionMinutes).put("ttsEngine", ttsEngine)
        .put("ttsVoice", ttsVoice).put("ttsPackage", ttsPackage).put("pitch", pitch)

    companion object {
        private val fields = setOf("id", "name", "userName", "description", "personality", "tone", "formal", "humor", "vocabulary", "instructions", "voice", "language", "detailed", "speed", "activationPhrases", "tolerantActivation", "avatar", "confirmationSound", "welcomeMessage", "sessionMinutes", "ttsEngine", "ttsVoice", "ttsPackage", "pitch")
        fun parse(j: JSONObject): AssistantProfile {
            require(j.keys().asSequence().all { it in fields }) { "El perfil contiene campos desconocidos. No se admiten credenciales." }
            return AssistantProfile(j.getString("id"), j.getString("name"), j.optString("userName"), j.optString("description"),
                j.optString("personality"), j.optString("tone"), j.optBoolean("formal"), j.optString("humor"), j.optString("vocabulary"),
                j.optString("instructions"), j.getString("voice"), j.getString("language"), j.optBoolean("detailed"), j.optDouble("speed", 1.0),
                activationPhrases = if (j.has("activationPhrases")) j.getJSONArray("activationPhrases").let { a -> (0 until a.length()).map { ActivationPhrase.parse(a.getJSONObject(it)) } } else listOf(ActivationPhrase(id = "principal", primary = true)),
                tolerantActivation = j.optBoolean("tolerantActivation"), avatar = j.optString("avatar"), confirmationSound = j.optBoolean("confirmationSound"),
                welcomeMessage = j.optString("welcomeMessage"), sessionMinutes = j.optInt("sessionMinutes", 10), ttsEngine = j.optString("ttsEngine", "realtime"),
                ttsVoice = j.optString("ttsVoice"), ttsPackage = j.optString("ttsPackage"), pitch = j.optDouble("pitch", 1.0)).validated()
        }
        fun export(profiles: List<AssistantProfile>): String {
            val text = JSONObject().put("version", 2).put("profiles", JSONArray().apply { profiles.forEach { put(it.validated().json()) } }).toString(2)
            require(text.toByteArray(Charsets.UTF_8).size <= 262144) { "Los perfiles superan los 256 KiB. Reduce las instrucciones o el número de perfiles." }
            return text
        }
        fun import(text: String): List<AssistantProfile> {
            require(text.length <= 262144) { "El archivo supera los 256 KiB." }
            val root = JSONObject(text)
            require(root.keys().asSequence().toSet() == setOf("version", "profiles") && root.getInt("version") in 1..2) { "Formato de perfiles no compatible." }
            val list = root.getJSONArray("profiles")
            require(list.length() in 1..50) { "Importa entre 1 y 50 perfiles." }
            return (0 until list.length()).map { parse(list.getJSONObject(it)) }
        }
        fun template(name: String): AssistantProfile = when (name) {
            "Jarvis" -> AssistantProfile(name = name, personality = "Elegante, eficiente y analítico; inspiración tecnológica de ciencia ficción", formal = true, humor = "Sutil")
            "Friday" -> AssistantProfile(name = name, personality = "Profesional, amable, directa y resolutiva", tone = "Natural, rápido y moderno")
            "PbtAdmin" -> AssistantProfile(name = name, personality = "Especialista en informática, sistemas, redes, servidores y programación", vocabulary = "Técnico, diagnóstico estructurado", detailed = true)
            else -> AssistantProfile()
        }
    }
}

object RealtimeSettings {
    const val model = "gpt-realtime"
    val voices = listOf("marin", "cedar", "alloy", "ash", "ballad", "coral", "echo", "sage", "shimmer", "verse")
    val speechVoices = voices + listOf("fable", "onyx", "nova")
    fun session(profile: AssistantProfile): JSONObject = JSONObject().put("type", "realtime").put("model", model)
        .put("instructions", profile.validated().prompt()).put("output_modalities", JSONArray().put(if (VoiceSynthesisContract.alternative(profile)) "text" else "audio"))
        .put("max_output_tokens", 1024).put("audio", JSONObject()
            .put("input", JSONObject().put("turn_detection", JSONObject().put("type", "server_vad").put("create_response", true).put("interrupt_response", true)))
            .apply { if (!VoiceSynthesisContract.alternative(profile)) put("output", JSONObject().put("voice", profile.voice).put("speed", profile.speed)) })
    fun secretRequest(profile: AssistantProfile): String = JSONObject()
        .put("expires_after", JSONObject().put("anchor", "created_at").put("seconds", 60))
        .put("session", session(profile)).toString()
    fun confirms(session: JSONObject?, profile: AssistantProfile): Boolean {
        if (session == null) return false
        val model = session.optString("model")
        if (!(model == this.model || model.startsWith("${this.model}-")) || session.optString("instructions") != profile.prompt()) return false
        if (VoiceSynthesisContract.alternative(profile)) return session.optJSONArray("output_modalities")?.let { it.length() == 1 && it.optString(0) == "text" } == true
        if (session.optJSONArray("output_modalities")?.let { it.length() == 1 && it.optString(0) == "audio" } != true) return false
        val output = session.optJSONObject("audio")?.optJSONObject("output") ?: return false
        return output.optString("voice") == profile.voice &&
            kotlin.math.abs(output.optDouble("speed", Double.NaN) - profile.speed) < 0.001
    }
}

object WakePhrase {
    private fun normalize(value: String) = Normalizer.normalize(value.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    fun matches(heard: String, name: String): Boolean {
        val target = normalize(name)
        val words = normalize(heard).split(" ")
        // Solo frase completa: no activar por citas, frases parciales ni peticiones concatenadas.
        if (target.isBlank() || words.firstOrNull() != "atento") return false
        val actual = words.drop(1).joinToString(" ")
        if (actual == target) return true
        if (target.length < 5 || ' ' in target || ' ' in actual || kotlin.math.abs(target.length - actual.length) > 1) return false
        var previous = IntArray(actual.length + 1) { it }
        target.forEachIndexed { i, a ->
            val row = IntArray(actual.length + 1); row[0] = i + 1
            actual.forEachIndexed { j, b -> row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1, previous[j] + if (a == b) 0 else 1) }
            previous = row
        }
        return previous.last() <= 1
    }
}
