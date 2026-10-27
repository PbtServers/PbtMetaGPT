package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer

/** Configuración sin secretos. Una cuenta Free es responsabilidad del titular: la API no certifica su plan. */
class HostedSettings(context: Context) {
    private val prefs = context.getSharedPreferences("servicios_alojados", Context.MODE_PRIVATE)
    val provider: String get() = prefs.getString("provider", if (KeyVault(context).exists()) "openai" else "groq").orEmpty()
    private val context = context.applicationContext
    val model get() = prefs.getString("model", "openai/gpt-oss-20b").orEmpty()
    val authorized get() = prefs.getBoolean("groq_free_authorized", false)
    val qwenAuthorized get() = prefs.getBoolean("qwen_authorized", false)
    fun select(value: String) { require(value in setOf("groq", "openai")); check(prefs.edit().putString("provider", value).commit()) }
    fun saveGroq(model: String, consent: Boolean) { check(prefs.edit().putString("model", HostedPolicy.model(model)).putBoolean("groq_free_authorized", consent).commit()) }
    fun authorizeQwen(value: Boolean) { check(prefs.edit().putBoolean("qwen_authorized", value).commit()) }
}

/** Captura Vosk acotada por turno. Ningún audio sale del dispositivo ni se conserva. */
class HostedDictation(private val context: Context) {
    @android.annotation.SuppressLint("MissingPermission") // comprobación explícita inmediatamente antes de AudioRecord
    suspend fun capture(): String = withContext(Dispatchers.IO) {
        require(WakeModel(context).installed()) { "Instala el modelo Vosk desde Activación por voz o escribe tu petición." }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw HostedFailure("Falta permiso de micrófono.")
        val gate = (context.applicationContext as PbtApplication).microphone
        val lease = gate.acquire(MicrophoneOwner.HOSTED_LOCAL) ?: throw HostedFailure("Micrófono ocupado. Detén la otra escucha.")
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(WakeModel(context).model.absolutePath).use { model -> Recognizer(model, 16000f).use { recognizer ->
                val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                check(minimum > 0)
                val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
                val buffer = ShortArray(3200)
                try {
                    check(recorder.state == AudioRecord.STATE_INITIALIZED)
                    recorder.startRecording()
                    check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    val until = android.os.SystemClock.elapsedRealtime() + 20000
                    while (android.os.SystemClock.elapsedRealtime() < until) {
                        ensureActive()
                        check(recorder.activeRecordingConfiguration?.isClientSilenced != true)
                        val n = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        check(n >= 0)
                        if (n > 0 && recognizer.acceptWaveForm(buffer, n)) {
                            val text = JSONObject(recognizer.result).optString("text").trim()
                            if (text.isNotEmpty()) return@withContext text
                        }
                        buffer.fill(0); delay(20)
                    }
                    JSONObject(recognizer.finalResult).optString("text").trim().ifEmpty { throw HostedFailure("No se reconoció voz en 20 segundos. Pulsa Dictar para otro turno o escribe.") }
                } finally {
                    buffer.fill(0)
                    try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                    finally { recorder.release() }
                }
            } }
        } catch (_: LinkageError) { throw HostedFailure("El runtime local Vosk no está disponible para este dispositivo. Puedes escribir tu petición.") }
        finally { gate.release(lease) }
    }
}

/** Ruta gratuita independiente: texto Groq + voz elegida; jamás recurre a OpenAI o a TTS facturable. */
class HostedConversation(private val context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(VoiceState(status = "GroqCloud · sin sesión activa"))
    val state = mutable.asStateFlow()
    private val settings = HostedSettings(context)
    private val api = GroqApi()
    private val player = PcmPlayer()
    private val speech = AlternativeSpeech(context, scope, {}, {})
    private var job: Job? = null
    private var draining: Job? = null
    private var generation = 0L
    private var limit: Job? = null
    private var profile: AssistantProfile? = null
    private var route: AudioRoute? = null
    private val history = mutableListOf<Pair<String, String>>()
    fun start(target: AssistantProfile, dictate: Boolean = false) {
        stop()
        try {
            check(settings.provider == "groq" && settings.authorized) { "Selecciona Groq Free y autoriza el envío de texto en Servicios de IA." }
            check(KeyVault(context, provider = "groq").exists()) { "Guarda una clave gratuita de GroqCloud en Servicios de IA." }
            profile = target.validated()
            mutable.value = VoiceState(active = true, connecting = dictate, sessionConfirmed = false, status = "GroqCloud · ${settings.model} · sesión local iniciada; conexión aún sin comprobar", route = "Captura Vosk: micrófono del teléfono. Salida según ruta elegida.")
            limit = scope.launch { delay(target.sessionMinutes * 60000L); stop() }
            if (dictate) listen()
        } catch (problem: Exception) { mutable.value = VoiceState(failed = true, status = problem.message ?: "Revisa Servicios de IA.") }
    }
    fun listen() {
        if (!mutable.value.active || job?.isActive == true) return
        if (profile?.language?.startsWith("es") != true) { mutable.update { it.copy(status = "El modelo Vosk instalado es español. Para otros idiomas escribe el texto.") }; return }
        val current = generation
        job = scope.launch {
            try {
                draining?.join(); ensureActive()
                mutable.update { it.copy(connecting = true, failed = false, status = "Escuchando con Vosk local · hasta 20 segundos") }
                ContinuousWakeService.beforeRealtime() // liberar captura de activación antes del turno
                val text = HostedDictation(context).capture()
                generate(text)
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { failed(problem) }
            finally { if (current == generation) mutable.update { it.copy(connecting = false, responding = false) } }
        }
    }
    fun send(text: String) {
        if (!mutable.value.active || job?.isActive == true) return
        val current = generation
        job = scope.launch {
            try { draining?.join(); ensureActive(); generate(text) }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { failed(problem) }
            finally { if (current == generation) mutable.update { it.copy(connecting = false, responding = false) } }
        }
    }
    private suspend fun generate(text: String) {
        require(text.trim().length in 1..2000 && !SecretPatterns.contains(text)) { "Petición vacía, demasiado larga o con patrones de credenciales." }
        check(settings.authorized && settings.provider == "groq")
        val target = requireNotNull(profile)
        var conversation = (history + ("user" to text.trim())).takeLast(9).dropWhile { it.first != "user" }
        while (conversation.sumOf { it.second.length } > 15000 && conversation.size > 1) conversation = conversation.drop(2)
        val instruction = target.prompt().take(8000) + if (target.ttsEngine == "qwen_space") "\nResponde con menos de 700 caracteres para la síntesis de voz." else ""
        val messages = listOf("system" to instruction) + conversation
        mutable.update { it.copy(connecting = true, failed = false, status = "GroqCloud · generando respuesta…") }
        ContinuousWakeService.beforeRealtime()
        val result = api.answer(KeyVault(context, provider = "groq").read(), settings.model, messages)
        history.clear(); history.addAll(conversation + ("assistant" to result))
        mutable.update { it.copy(connecting = false, responding = true, sessionConfirmed = true, transcript = "Tú: $text\n\nGroqCloud · ${settings.model}: $result", status = "GroqCloud · respuesta recibida") }
        if (HostedPolicy.freeVoice(target.ttsEngine)) play(target, result)
        else mutable.update { it.copy(status = "Respuesta de GroqCloud lista. Elige Android TTS o Qwen3-TTS en Voz para audio gratuito; no se ha llamado a otro proveedor.") }
    }
    private suspend fun play(target: AssistantProfile, text: String) {
        val pcm = if (target.ttsEngine == "qwen_space") {
            check(settings.qwenAuthorized) { "Autoriza Qwen3-TTS en la biblioteca antes de transmitir texto al Space público." }
            QwenSpaceApi().synthesize(target, text) { message -> mutable.update { it.copy(status = message) } }
        } else { speech.prepare(target); speech.synthesize(target, text) }
        try {
            val preferBluetooth = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE).getBoolean("audio-bluetooth", false)
            route = AudioRoute(context) { stop() }
            val label = route!!.start(preferBluetooth)
            mutable.update { it.copy(route = label, status = "Reproduciendo respuesta · ${VoiceSynthesisContract.label(target.ttsEngine)}") }
            player.play(pcm)
            mutable.update { it.copy(status = "Turno finalizado · pulsa Dictar o escribe otra petición") }
        } finally { route?.close(); route = null }
    }
    private fun failed(problem: Exception) {
        // No se incluyen mensajes de motores, JSON o proveedores en diagnóstico/UI.
        val message = when (problem) {
            is HostedFailure -> problem.publicMessage
            is IllegalArgumentException -> "Revisa petición, idioma y selección de voz. No se ha cambiado de proveedor."
            else -> "No se pudo completar el turno. Revisa credenciales, consentimiento, red y voz seleccionada."
        }
        mutable.update { it.copy(status = message, failed = true) }
    }
    fun stop() {
        generation++
        job?.let { it.cancel(); draining = it }; job = null; limit?.cancel(); limit = null
        player.stop(); speech.stop(); route?.close(); route = null; history.clear(); profile = null
        mutable.value = VoiceState(status = "GroqCloud · conversación detenida; contexto borrado")
    }
}
