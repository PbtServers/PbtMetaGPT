package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.hostedBytes(limit: Int = 1048576): ByteArray = suspendCancellableCoroutine { cont ->
    cont.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            if (cont.isActive) cont.resumeWithException(HostedFailure("No se pudo conectar por HTTPS. Revisa la red y la disponibilidad del proveedor."))
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val bytes = response.use {
                    if (!it.isSuccessful) throw HostedPolicy.failure(it.code, it.header("Retry-After"))
                    val body = it.body ?: throw IOException()
                    if (body.contentLength() > limit) throw IOException()
                    body.source().use { source ->
                        val out = okio.Buffer()
                        while (!source.exhausted()) {
                            if (!cont.isActive) throw IOException()
                            val count = source.read(out, minOf(8192L, limit + 1L - out.size))
                            if (count < 0) break
                            if (out.size > limit) throw IOException()
                        }
                        out.readByteArray()
                    }
                }
                if (cont.isActive) cont.resume(bytes)
            } catch (problem: Exception) {
                if (cont.isActive) cont.resumeWithException(problem as? HostedFailure ?: HostedFailure("Respuesta del proveedor inválida o demasiado grande."))
            }
        }
    })
}

/** API pública de GroqCloud; no usa servicios de OpenAI aunque el protocolo sea compatible. */
class GroqApi(private val http: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false)
    .followSslRedirects(false).callTimeout(45, TimeUnit.SECONDS).build()) {
    private fun request(path: String, key: String) = Request.Builder().url("https://api.groq.com/openai/v1/$path")
        .header("Authorization", "Bearer $key")
    suspend fun models(key: String): List<String> {
        val body = JSONObject(String(http.newCall(request("models", key).build()).hostedBytes(), Charsets.UTF_8)).getJSONArray("data")
        return (0 until body.length()).map { body.getJSONObject(it).getString("id") }.sorted()
    }
    suspend fun answer(key: String, model: String, messages: List<Pair<String, String>>): String {
        require(messages.size in 2..18 && messages.sumOf { it.second.length } <= 24000)
        val data = JSONObject().put("model", HostedPolicy.model(model)).put("max_completion_tokens", 1024).put("stream", false)
            .put("messages", JSONArray().apply { messages.forEach { (role, text) -> put(JSONObject().put("role", role).put("content", text)) } })
        if (model.startsWith("openai/gpt-oss-")) data.put("reasoning_effort", "low")
        val body = http.newCall(request("chat/completions", key).post(data.toString().toRequestBody("application/json".toMediaType())).build()).hostedBytes()
        val text = JSONObject(String(body, Charsets.UTF_8)).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content").trim()
        if (text.isEmpty() || text.length > 4000) throw HostedFailure("Respuesta vacía o demasiado larga para voz. Prueba una petición más breve.")
        return text
    }
}

/** Contrato CustomVoice del Space oficial. Sin VoiceDesign, clones ni carga de modelos. */
object QwenSpaceContract {
    const val origin = "https://qwen-qwen3-tts.hf.space"
    const val function = "generate_custom_voice"
    val speakers = listOf("Aiden", "Dylan", "Eric", "Ono_anna", "Ryan", "Serena", "Sohee", "Uncle_fu", "Vivian")
    val sizes = listOf("0.6B", "1.7B")
    val languages = listOf("Auto", "Chinese", "English", "Japanese", "Korean", "French", "German", "Spanish", "Portuguese", "Russian")
    fun language(tag: String) = when (tag.substringBefore('-').lowercase()) {
        "es" -> "Spanish"; "en" -> "English"; "zh" -> "Chinese"; "ja" -> "Japanese"; "ko" -> "Korean"
        "fr" -> "French"; "de" -> "German"; "pt" -> "Portuguese"; "ru" -> "Russian"
        else -> throw HostedFailure("Qwen3-TTS no documenta ese idioma. Catalán no está disponible; elige otra voz compatible.")
    }
    fun data(profile: AssistantProfile, text: String): JSONObject {
        require(profile.ttsVoice in speakers && profile.ttsPackage in sizes && text.length in 1..1000 && !SecretPatterns.contains(text))
        return JSONObject().put("data", JSONArray().put(text).put(language(profile.language)).put(profile.ttsVoice).put("").put(profile.ttsPackage))
    }
    fun verifyInfo(info: JSONObject) {
        val endpoint = info.getJSONObject("named_endpoints").getJSONObject("/$function")
        val parameters = endpoint.getJSONArray("parameters")
        require((0 until parameters.length()).map { parameters.getJSONObject(it).getString("parameter_name") } == listOf("text", "language", "speaker", "instruct", "model_size")) { "El Space ha cambiado su contrato; integración desactivada." }
        val expected = mapOf(1 to languages, 2 to speakers, 4 to sizes)
        expected.forEach { (index, values) ->
            val type = parameters.getJSONObject(index).getJSONObject("type").getJSONArray("enum")
            require((0 until type.length()).map { type.getString(it) }.containsAll(values)) { "El Space ya no ofrece las opciones documentadas." }
        }
    }
    fun fileUrl(value: String): String {
        val url = value.toHttpUrlOrNull() ?: throw HostedFailure("URL de audio no válida.")
        require(url.scheme == "https" && url.host == "qwen-qwen3-tts.hf.space" && url.port == 443 && url.username.isEmpty() && url.password.isEmpty()
            && url.encodedPath.startsWith("/gradio_api/file=") && url.query == null && url.fragment == null) { "El audio debe proceder del Space autorizado." }
        return url.toString()
    }
    fun result(stream: String): JSONArray {
        val events = stream.replace("\r\n", "\n").split("\n\n")
        fun data(block: String) = block.lineSequence().filter { it.startsWith("data: ") }.joinToString("\n") { it.removePrefix("data: ") }
        val error = events.firstOrNull { it.lineSequence().any { line -> line == "event: error" } }
        if (error != null) {
            val quota = data(error).lowercase().let { "quota" in it || "exceeded" in it }
            throw HostedFailure(if (quota) "Cuota gratuita Qwen3-TTS agotada. Espera al reinicio indicado por Hugging Face; no se reintenta ni se cambia de proveedor."
                else "El Space rechazó la generación. GPU, acceso o servicio no disponibles.")
        }
        val complete = events.firstOrNull { it.lineSequence().any { line -> line == "event: complete" } }
            ?: throw HostedFailure("El Space no completó el audio. Cola agotada o servicio no disponible.")
        return JSONArray(data(complete))
    }
}

class QwenSpaceApi(private val http: OkHttpClient = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
    .callTimeout(150, TimeUnit.SECONDS).readTimeout(140, TimeUnit.SECONDS).build()) {
    suspend fun probe() {
        val request = Request.Builder().url("${QwenSpaceContract.origin}/gradio_api/info").build()
        try { QwenSpaceContract.verifyInfo(JSONObject(String(http.newCall(request).hostedBytes(), Charsets.UTF_8))) }
        catch (problem: HostedFailure) { throw problem }
        catch (_: Exception) { throw HostedFailure("El Space no expone el contrato CustomVoice documentado. Servicio no disponible.") }
    }
    suspend fun synthesize(profile: AssistantProfile, text: String, status: (String) -> Unit): PcmAudio {
        if (text.length > 1000) throw HostedFailure("El texto supera los 1000 caracteres admitidos por esta integración Qwen. La respuesta sigue visible; pide una respuesta más breve o elige Android TTS.")
        probe() // comprobar contrato y opciones antes de transmitir texto
        status("Qwen3-TTS · enviando una solicitud a la cola pública…")
        val base = "${QwenSpaceContract.origin}/gradio_api/call/${QwenSpaceContract.function}"
        val submission = Request.Builder().url(base).post(QwenSpaceContract.data(profile, text).toString().toRequestBody("application/json".toMediaType())).build()
        val id = JSONObject(String(http.newCall(submission).hostedBytes(), Charsets.UTF_8)).getString("event_id")
        require(id.matches(Regex("[a-fA-F0-9-]{16,64}")))
        status("Qwen3-TTS · en cola / generando; hasta 150 segundos. Puedes cancelar.")
        // Gradio publica eventos SSE, no un audio inmediato. Lectura acotada y cancelable.
        val eventBytes = http.newCall(Request.Builder().url("$base/$id").build()).hostedBytes()
        val data = QwenSpaceContract.result(String(eventBytes, Charsets.UTF_8))
        val audio = data.optJSONObject(0) ?: throw HostedFailure("Qwen3-TTS no devolvió audio. Revisa cuota y disponibilidad en el Space oficial.")
        val url = QwenSpaceContract.fileUrl(audio.getString("url"))
        val bytes = http.newCall(Request.Builder().url(url).build()).hostedBytes(16777216)
        return VoiceSynthesisContract.wav(bytes)
    }
}
