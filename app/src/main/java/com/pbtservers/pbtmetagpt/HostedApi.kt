package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
class GroqApi(private val http: OkHttpClient = HostedHttp.groq,
              private val apiBase: String = "https://api.groq.com/openai/v1/") {
    private fun request(path: String, key: String) = Request.Builder().url("${apiBase}$path")
        .header("Authorization", "Bearer $key")
    suspend fun models(key: String): List<String> {
        val body = JSONObject(String(http.newCall(request("models", key).build()).hostedBytes(), Charsets.UTF_8)).getJSONArray("data")
        return (0 until body.length()).map { body.getJSONObject(it).getString("id") }.sorted()
    }
    suspend fun transcribe(key: String, wav: ByteArray): String {
        require(wav.size in 44..1_000_000 && wav.copyOfRange(0, 4).contentEquals("RIFF".toByteArray(Charsets.US_ASCII)))
        val payload = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("file", "grabacion.wav", wav.toRequestBody("audio/wav".toMediaType()))
            .addFormDataPart("model", "whisper-large-v3-turbo")
            .addFormDataPart("language", "es")
            .addFormDataPart("response_format", "json")
            .build()
        val req = request("audio/transcriptions", key).post(payload).build()
        val result = JSONObject(String(http.newCall(req).hostedBytes(), Charsets.UTF_8)).optString("text").trim()
        if (result.isBlank() || result.length > 2000) throw HostedFailure("Groq Whisper no reconoció una petición válida.")
        return result
    }
    suspend fun answer(key: String, model: String, messages: List<Pair<String, String>>): String {
        return answerStream(key, model, messages) {}
    }
    /** Imagen privada enviada solo tras una confirmación explícita; sin URLs públicas ni almacenamiento remoto propio. */
    suspend fun analyzeImage(key: String, jpeg: ByteArray, question: String = "Describe esta imagen en español y explica lo importante."): String {
        require(jpeg.size in 100..4_000_000 && jpeg[0] == 0xff.toByte() && jpeg[1] == 0xd8.toByte())
        require(question.length in 1..500 && !SecretPatterns.contains(question))
        val image = "data:image/jpeg;base64," + java.util.Base64.getEncoder().encodeToString(jpeg)
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", question))
            .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", image)))
        val data = JSONObject().put("model", "qwen/qwen3.8-27b").put("max_completion_tokens", 700)
            .put("reasoning_effort", "none")
            .put("stream", false).put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val req = request("chat/completions", key).post(data.toString().toRequestBody("application/json".toMediaType())).build()
        val response = JSONObject(String(http.newCall(req).hostedBytes(1_048_576), Charsets.UTF_8))
            .getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content").trim()
        if (response.isBlank() || response.length > 4000) throw HostedFailure("Groq Vision no ha devuelto una descripción válida.")
        return response
    }
    suspend fun answerStream(key: String, model: String, messages: List<Pair<String, String>>, delta: suspend (String) -> Unit): String {
        require(messages.size in 2..18 && messages.sumOf { it.second.length } <= 24000)
        val data = JSONObject().put("model", HostedPolicy.model(model)).put("max_completion_tokens", 1024).put("stream", true)
            .put("messages", JSONArray().apply { messages.forEach { (role, text) -> put(JSONObject().put("role", role).put("content", text)) } })
        if (model.startsWith("openai/gpt-oss-")) data.put("reasoning_effort", "low")
        val req = request("chat/completions", key).post(data.toString().toRequestBody("application/json".toMediaType())).build()
        val result = StringBuilder()
        callbackFlow<String> {
            val call = http.newCall(req)
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { close(HostedFailure("No se pudo completar el streaming de Groq. Revisa la red.")) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use { reply ->
                            if (!reply.isSuccessful) throw HostedPolicy.failure(reply.code, reply.header("Retry-After"))
                            require(reply.header("Content-Type").orEmpty().startsWith("text/event-stream"))
                            val source = requireNotNull(reply.body).source()
                            var received = 0L; var done = false; var characters = 0
                            while (!isClosedForSend && !source.exhausted()) {
                                val line = source.readUtf8LineStrict(65536)
                                received += line.length; require(received <= 1048576)
                                if (!line.startsWith("data:")) continue
                                val event = line.removePrefix("data:").trim()
                                if (event == "[DONE]") { done = true; break }
                                val part = GroqStream.content(event)
                                characters += part.length; require(characters <= 4000)
                                if (part.isNotEmpty() && trySendBlocking(part).isFailure) break
                            }
                            if (!done && !isClosedForSend) throw HostedFailure("Groq interrumpió la respuesta. El texto parcial sigue visible.")
                        }
                        close()
                    } catch (problem: Exception) { close(problem as? HostedFailure ?: HostedFailure("Streaming Groq inválido o demasiado grande.")) }
                }
            })
            awaitClose { call.cancel() }
        }.collect { part -> result.append(part); delta(part) }
        val text = result.toString().trim()
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

class QwenSpaceApi(private val http: OkHttpClient = HostedHttp.qwen) {
    private val probeLock = Mutex()
    private var verifiedAt = 0L
    suspend fun probe(force: Boolean = true, metrics: VoiceLatency? = null) = probeLock.withLock {
        val now = System.nanoTime() / 1000000
        if (!force && verifiedAt != 0L && now - verifiedAt < 300000) return@withLock
        val request = Request.Builder().url("${QwenSpaceContract.origin}/gradio_api/info").tag(VoiceLatency::class.java, metrics).build()
        try { QwenSpaceContract.verifyInfo(JSONObject(String(http.newCall(request).hostedBytes(), Charsets.UTF_8))); verifiedAt = now }
        catch (cancel: CancellationException) { throw cancel }
        catch (problem: HostedFailure) { throw problem }
        catch (_: Exception) { throw HostedFailure("El Space no expone el contrato CustomVoice documentado. Servicio no disponible.") }
    }
    suspend fun synthesize(profile: AssistantProfile, text: String, status: (String) -> Unit, metrics: VoiceLatency? = null): PcmAudio {
        if (text.length > 1000) throw HostedFailure("El texto supera los 1000 caracteres admitidos por esta integración Qwen. La respuesta sigue visible; pide una respuesta más breve o elige Android TTS.")
        probe(force = false, metrics = metrics) // solo metadatos no sensibles, TTL 5 minutos; nunca caché de texto/audio
        status("Qwen3-TTS · enviando una solicitud a la cola pública…")
        val base = "${QwenSpaceContract.origin}/gradio_api/call/${QwenSpaceContract.function}"
        val submission = Request.Builder().url(base).tag(VoiceLatency::class.java, metrics).post(QwenSpaceContract.data(profile, text).toString().toRequestBody("application/json".toMediaType())).build()
        val submitted = System.nanoTime()
        val id = JSONObject(String(http.newCall(submission).hostedBytes(), Charsets.UTF_8)).getString("event_id")
        require(id.matches(Regex("[a-fA-F0-9-]{16,64}")))
        metrics?.add(VoiceStage.TTS_SUBMIT, (System.nanoTime() - submitted) / 1000000)
        status("Qwen3-TTS · esperando cola/generación. Puedes cancelar.")
        // Gradio publica eventos SSE, no un audio inmediato. Lectura acotada y cancelable.
        val waiting = System.nanoTime()
        val eventBytes = http.newCall(Request.Builder().url("$base/$id").tag(VoiceLatency::class.java, metrics).build()).hostedBytes()
        metrics?.add(VoiceStage.TTS_WAIT_COMBINED, (System.nanoTime() - waiting) / 1000000)
        val data = QwenSpaceContract.result(String(eventBytes, Charsets.UTF_8))
        val audio = data.optJSONObject(0) ?: throw HostedFailure("Qwen3-TTS no devolvió audio. Revisa cuota y disponibilidad en el Space oficial.")
        val url = QwenSpaceContract.fileUrl(audio.getString("url"))
        val download = System.nanoTime()
        val bytes = http.newCall(Request.Builder().url(url).tag(VoiceLatency::class.java, metrics).build()).hostedBytes(16777216)
        metrics?.add(VoiceStage.TTS_DOWNLOAD, (System.nanoTime() - download) / 1000000)
        return VoiceSynthesisContract.wav(bytes)
    }
}

object GroqStream {
    fun content(data: String): String {
        val event = JSONObject(data)
        if (event.has("error")) throw HostedFailure("Groq rechazó el turno durante el streaming.")
        return event.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
    }
}
