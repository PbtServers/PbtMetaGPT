package com.pbtservers.pbtmetagpt

import android.content.Context
import org.json.JSONObject
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class ExternalVoiceConfig(val endpoint: String, val model: String, val sampleRate: Int = 24000) {
    fun validate(): ExternalVoiceConfig {
        val uri = runCatching { URI(endpoint) }.getOrNull()
        require(uri != null && uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "La API TTS necesita una dirección HTTPS sin credenciales ni parámetros." }
        require(model.length in 1..100 && !SecretPatterns.contains(model) && sampleRate in setOf(16000, 22050, 24000, 44100, 48000)) { "Modelo o frecuencia de la API TTS no válidos." }
        return this
    }
}

object VoiceSynthesisContract {
    fun alternative(profile: AssistantProfile) = profile.ttsEngine != "realtime"
    fun request(profile: AssistantProfile, text: String, endpoint: String, model: String, token: String?): Request {
        ExternalVoiceConfig(endpoint, model).validate()
        require(text.length in 1..4000 && !SecretPatterns.contains(text)) { "Texto de síntesis demasiado largo o con patrones de secretos." }
        val voice = if (profile.ttsEngine == "openai_tts") profile.voice else profile.ttsVoice
        require(voice.isNotBlank()) { "Selecciona una voz para este motor." }
        val json = JSONObject().put("model", model).put("voice", voice).put("input", text).put("response_format", "pcm").put("speed", profile.speed)
        if (profile.ttsEngine == "openai_tts") json.put("instructions", "Idioma: ${profile.language}. Tono y expresión: ${profile.tone}")
        return Request.Builder().url(endpoint).apply {
            if (!token.isNullOrBlank()) { require(token.none { it == '\r' || it == '\n' }); header("Authorization", "Bearer $token") }
        }.post(json.toString().toRequestBody("application/json".toMediaType())).build()
    }
    fun wav(bytes: ByteArray): PcmAudio {
        require(bytes.size in 44..16777216 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "El motor Android no devolvió WAV PCM compatible." }
        val view = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var position = 12; var rate = 0; var valid = false; var pcm: ByteArray? = null
        while (position + 8 <= bytes.size) {
            val name = String(bytes, position, 4, Charsets.US_ASCII); val length = view.getInt(position + 4)
            require(length >= 0 && length <= bytes.size - position - 8)
            if (name == "fmt ") {
                require(length >= 16)
                rate = view.getInt(position + 12)
                valid = view.getShort(position + 8).toInt() == 1 && view.getShort(position + 10).toInt() == 1 && view.getShort(position + 22).toInt() == 16
            }
            if (name == "data") pcm = bytes.copyOfRange(position + 8, position + 8 + length)
            position += 8 + length + length % 2
        }
        require(valid && rate in 8000..48000 && pcm != null && pcm!!.isNotEmpty() && pcm!!.size % 2 == 0) { "Solo se admite audio PCM16 mono con frecuencia compatible." }
        return PcmAudio(pcm!!, rate)
    }
}

class VoiceApiSettings(context: Context) {
    private val prefs = context.getSharedPreferences("tts_api_publica", Context.MODE_PRIVATE)
    fun load() = ExternalVoiceConfig(prefs.getString("endpoint", "").orEmpty(), prefs.getString("model", "").orEmpty(), prefs.getInt("sampleRate", 24000))
    fun authorized() = prefs.getBoolean("authorized", false)
    fun save(config: ExternalVoiceConfig) { config.validate(); check(prefs.edit().putString("endpoint", config.endpoint).putString("model", config.model).putInt("sampleRate", config.sampleRate).putBoolean("authorized", true).commit()) }
}
