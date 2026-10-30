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
import org.vosk.LibVosk
import org.vosk.LogLevel

/** Configuración sin secretos. Una cuenta Free es responsabilidad del titular: la API no certifica su plan. */
class HostedSettings(context: Context) {
    private val prefs = context.getSharedPreferences("servicios_alojados", Context.MODE_PRIVATE)
    val provider: String get() = prefs.getString("provider", if (KeyVault(context).exists()) "openai" else "groq").orEmpty()
    private val context = context.applicationContext
    val model get() = prefs.getString("model", "openai/gpt-oss-20b").orEmpty()
    val authorized get() = prefs.getBoolean("groq_free_authorized", false)
    val qwenAuthorized get() = prefs.getBoolean("qwen_authorized", false)
    val groqAudioAuthorized get() = prefs.getBoolean("groq_audio_authorized", false)
    val recognitionMode get() = runCatching { RecognitionMode.valueOf(prefs.getString("reconocimiento_modo", "PRECISION").orEmpty()) }.getOrDefault(RecognitionMode.PRECISION)
    fun recognitionMode(value: RecognitionMode) { check(prefs.edit().putString("reconocimiento_modo", value.name).commit()) }
    val dictationSource get() = runCatching { DictationSource.valueOf(prefs.getString("dictado_motor", "AUTO").orEmpty()) }.getOrDefault(DictationSource.AUTO)
    fun dictationSource(value: DictationSource) { check(prefs.edit().putString("dictado_motor", value.name).commit()) }
    fun select(value: String) { require(value in setOf("groq", "openai")); check(prefs.edit().putString("provider", value).commit()) }
    fun saveGroq(model: String, consent: Boolean) { check(prefs.edit().putString("model", HostedPolicy.model(model)).putBoolean("groq_free_authorized", consent).commit()) }
    fun authorizeQwen(value: Boolean) { check(prefs.edit().putBoolean("qwen_authorized", value).commit()) }
    fun authorizeGroqAudio(value: Boolean) { check(prefs.edit().putBoolean("groq_audio_authorized", value).putString("dictado_motor", if (value) "GROQ_WHISPER" else "AUTO").commit()) }
}

/** El motor elegido se aplica también a la conversación continua. Activación siempre local. */
class HostedDictation(private val context: Context) {
    suspend fun capture(language: String = "es-ES", continuous: Boolean = false, ready: () -> Unit = {},
        speechStarted: () -> Unit = {}, speechEnded: () -> Unit = {}, engine: (RecognitionEngine) -> Unit = {}, assistantName: String = "", transcribing: () -> Unit = {}): String {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw HostedFailure("Falta permiso de micrófono.")
        val settings = HostedSettings(context)
        val offline = OnDeviceDictation(context)
        val selected = RecognitionPolicy.engine(settings.dictationSource,
            settings.provider == "groq" && settings.authorized && settings.groqAudioAuthorized, offline.available(), continuous)
        withContext(Dispatchers.Main) { engine(selected) }
        return when (selected) {
            RecognitionEngine.GROQ_WHISPER -> {
                if (settings.provider != "groq" || !settings.authorized || !settings.groqAudioAuthorized)
                    throw HostedFailure("Groq Whisper necesita autorización de audio en Activación por voz → Opciones avanzadas. Autorízalo allí o selecciona un motor local.")
                if (!language.startsWith("es")) throw HostedFailure("El dictado de Groq está configurado para español. Selecciona es-ES.")
                val recorded = GroqAudioCapture(context).capture(speechEnded, continuous, ready, speechStarted,
                    RecognitionPolicy.silenceMs(settings.recognitionMode))
                try {
                    val local = LocalRecordedCommand(context).recognize(recorded, assistantName, ListeningSettings(context).load())
                    if (local != null) { withContext(Dispatchers.Main) { engine(RecognitionEngine.VOSK) }; local }
                    else {
                        withContext(Dispatchers.Main) { transcribing() }
                        GroqApi().transcribe(KeyVault(context, provider = "groq").read(), recorded, RecognitionPolicy.whisperModel(settings.recognitionMode))
                    }
                }
                finally { recorded.fill(0) }
            }
            RecognitionEngine.ANDROID_LOCAL -> withContext(Dispatchers.Main.immediate) {
                val route = AudioRoute(context) {}
                try {
                    route.start(AudioInputSettings(context).useBluetooth(), speaker = true)
                    offline.capture(language, speechEnded, ready, speechStarted, RecognitionPolicy.silenceMs(settings.recognitionMode))
                } finally { route.close() }
            }
            RecognitionEngine.VOSK -> {
                if (!language.startsWith("es")) throw HostedFailure("Vosk instalado solo admite español. Selecciona es-ES o instala reconocimiento local de Android para tu idioma.")
                captureVosk(continuous, ready, speechStarted, speechEnded, RecognitionPolicy.silenceMs(settings.recognitionMode), assistantName)
            }
        }
    }
    private suspend fun captureVosk(continuous: Boolean, ready: () -> Unit, started: () -> Unit, ended: () -> Unit, silenceMs: Int, name: String): String = withContext(Dispatchers.IO) {
        if (!WakeModel(context).installed()) throw HostedFailure("Falta instalar el modelo español. Descárgalo desde Inicio.")
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val app = context.applicationContext as PbtApplication
        try {
            app.voskModels.acquire(WakeModel(context).model.absolutePath).use { model ->
                LocalCommandDecoder(WakeModel(context).model.absolutePath, sharedModel = model.model).use { recognizer ->
                    val transcript = mutableListOf<CommandHypothesis>()
                    try {
                        PcmSpeechCapture(context).capture(continuous, silenceMs, ready, started, ended) { frame, count ->
                            recognizer.accept(frame, count)?.takeIf { it.text.isNotBlank() }?.let(transcript::add)
                        }
                        recognizer.finish().takeIf { it.text.isNotBlank() }?.let(transcript::add)
                        val text = transcript.joinToString(" ") { it.text }.ifEmpty { throw NoSpeechDetected() }
                        if (!VoiceCommands.confident(text, transcript.minOf { it.confidence }, name, ListeningSettings(context).load()))
                            throw HostedFailure("La orden no se ha reconocido con suficiente confianza. No se ha ejecutado; pulsa Micrófono y repítela.")
                        text
                    } finally { transcript.clear() }
                }
            }
        } catch (_: LinkageError) { throw HostedFailure("El runtime Vosk no está disponible en este dispositivo. Puedes escribir tu petición.") }
    }
}

class NoSpeechDetected : Exception()
