package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.*
import okhttp3.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Un único trabajo y reproductor por controlador; toda interrupción invalida el audio pendiente. */
class AlternativeSpeech(private val context: Context, private val scope: CoroutineScope,
    private val status: (String) -> Unit, private val error: (String) -> Unit,
) {
    private val player = PcmPlayer()
    private val android = AndroidVoiceEngine(context)
    private val http = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(60, java.util.concurrent.TimeUnit.SECONDS).build()
    private val queue = SpeechTaskQueue(scope, ::synthesize, player::play, player::stop, status, error)
    fun speak(profile: AssistantProfile, text: String, finished: () -> Unit = {}) = queue.speak(profile, text, finished)
    suspend fun prepare(profile: AssistantProfile) {
        profile.validated()
        when (profile.ttsEngine) {
            "qwen_space" -> {
                check(HostedSettings(context).qwenAuthorized) { "Autoriza el envío de texto a Qwen3-TTS en la biblioteca de voces." }
                HostedHttp.qwenApi.probe()
            }
            "android" -> android.verify(profile)
            "sherpa_vits" -> {
                val pack = withContext(Dispatchers.IO) { VoicePackageStore(File(context.filesDir, "voces")).describe(profile.ttsVoice) }
                LocalModelClient(context).checkHardware(pack)
            }
            "external" -> {
                val settings = VoiceApiSettings(context)
                if (!settings.authorized()) throw DiagnosticFailure("TTS_AUTHORIZATION", "Autoriza y guarda el proveedor TTS externo antes de enviar texto.")
                settings.load().validate()
                val vault = KeyVault(context, externalTts = true); if (vault.exists()) vault.read()
            }
            "openai_tts" -> {
                if (!VoiceSynthesisContract.speechAvailable()) throw DiagnosticFailure("TTS_RETIRED", "OpenAI ha anunciado la retirada de Speech. Elige Realtime u otro motor.")
                KeyVault(context).read()
            }
        }
    }
    suspend fun synthesize(profile: AssistantProfile, text: String): PcmAudio = when (profile.ttsEngine) {
        "qwen_space" -> {
            check(HostedSettings(context).qwenAuthorized) { "Falta autorización para transmitir texto al Space Qwen3-TTS." }
            HostedHttp.qwenApi.synthesize(profile, text) { message -> scope.launch { status(message) } }
        }
        "android" -> android.synthesize(profile, text)
        "sherpa_vits" -> LocalModelClient(context).synthesize(VoicePackageStore(File(context.filesDir, "voces")), profile.ttsVoice, text, profile.speed)
        "openai_tts" -> {
            check(VoiceSynthesisContract.speechAvailable()) { "El proveedor ha anunciado la retirada de este endpoint. Selecciona Realtime u otro motor." }
            val request = VoiceSynthesisContract.request(profile, text, "https://api.openai.com/v1/audio/speech", "gpt-4o-mini-tts", KeyVault(context).read())
            PcmAudio(http.newCall(request).awaitPcm(), 24000)
        }
        "external" -> {
            val settings = VoiceApiSettings(context)
            check(settings.authorized()) { "Autoriza y guarda el proveedor TTS externo antes de enviar texto." }
            val config = settings.load().validate()
            val vault = KeyVault(context, externalTts = true)
            val token = if (vault.exists()) vault.read() else null
            val request = VoiceSynthesisContract.request(profile, text, config.endpoint, profile.ttsPackage.ifBlank { config.model }, token)
            PcmAudio(http.newCall(request).awaitPcm(), config.sampleRate)
        }
        else -> throw IllegalArgumentException("La voz Realtime usa exclusivamente su audio nativo.")
    }
    fun stop() { queue.stop(); android.close() }
}

internal suspend fun Call.awaitPcm(): ByteArray = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se recibió audio de la API TTS.")) }
        override fun onResponse(call: Call, response: Response) {
            try {
                val bytes = response.use {
                    check(it.isSuccessful) { "El proveedor TTS rechazó la solicitud." }
                    val type = it.header("Content-Type").orEmpty().lowercase(java.util.Locale.ROOT)
                    require(type.startsWith("audio/pcm") || type.startsWith("application/octet-stream")) { "El proveedor no devolvió el formato PCM contratado." }
                    val input = requireNotNull(it.body).byteStream()
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(16384)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 16777216); output.write(buffer, 0, n) }
                    output.toByteArray().also { pcm -> require(pcm.isNotEmpty() && pcm.size % 2 == 0 && !(pcm.size >= 4 && String(pcm, 0, 4, Charsets.US_ASCII) == "RIFF")) }
                }
                if (continuation.isActive) continuation.resume(bytes)
            } catch (_: Exception) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("La API TTS no devolvió PCM16 mono válido dentro del límite de tamaño.")) }
        }
    })
}
