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
    val groqAudioAuthorized get() = prefs.getBoolean("groq_audio_authorized", false)
    fun select(value: String) { require(value in setOf("groq", "openai")); check(prefs.edit().putString("provider", value).commit()) }
    fun saveGroq(model: String, consent: Boolean) { check(prefs.edit().putString("model", HostedPolicy.model(model)).putBoolean("groq_free_authorized", consent).commit()) }
    fun authorizeQwen(value: Boolean) { check(prefs.edit().putBoolean("qwen_authorized", value).commit()) }
    fun authorizeGroqAudio(value: Boolean) { check(prefs.edit().putBoolean("groq_audio_authorized", value).commit()) }
}

/** Captura Vosk acotada por turno. Ningún audio sale del dispositivo ni se conserva. */
class HostedDictation(private val context: Context) {
    suspend fun capture(): String {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw HostedFailure("Falta permiso de micrófono.")
        val settings = HostedSettings(context)
        if (settings.provider == "groq" && settings.authorized && settings.groqAudioAuthorized) {
            val recorded = GroqAudioCapture(context).capture()
            try {
                return GroqApi().transcribe(KeyVault(context, provider = "groq").read(), recorded)
            } finally {
                recorded.fill(0)
            }
        }
        // Usar reconocimiento libre del sistema para frases largas; Vosk permanece
        // disponible cuando Android no tiene un motor on-device instalado.
        val offline = OnDeviceDictation(context)
        if (offline.available()) return offline.capture("es-ES")
        return captureVosk()
    }
    private suspend fun captureVosk(): String = withContext(Dispatchers.IO) {
        require(WakeModel(context).installed()) { "Instala el modelo Vosk o el reconocimiento offline de Android para dictar." }
        // Android puede revocar RECORD_AUDIO entre la selección del motor y el fallback Vosk.
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw HostedFailure("Falta permiso de micrófono. Concédelo antes de dictar.")
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
        } catch (_: SecurityException) { throw HostedFailure("Android ha denegado el micrófono. Comprueba el permiso de la aplicación.") }
        catch (_: LinkageError) { throw HostedFailure("El runtime local Vosk no está disponible para este dispositivo. Puedes escribir tu petición.") }
        finally { gate.release(lease) }
    }
}
