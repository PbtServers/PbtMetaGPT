package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.PowerManager
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.IOException

/** ASR local continuo. No usa SpeechRecognizer, no guarda PCM ni transcripciones. */
class ContinuousWakeEngine(private val context: Context, private val scope: CoroutineScope) {
    private var work: Job? = null
    suspend fun stop() { work?.cancelAndJoin(); work = null }
    @android.annotation.SuppressLint("WakelockTimeout") // Solo durante captura autorizada del servicio visible.
    suspend fun start(profiles: List<AssistantProfile>, ready: () -> Unit, detected: (AssistantProfile, String) -> Unit, failed: (Throwable) -> Unit, input: (Int) -> Unit = {}) {
        stop()
        val gate = ActivationGate(); val epoch = gate.arm(profiles)
        work = scope.launch(Dispatchers.IO) {
            val gatekeeper = (context.applicationContext as PbtApplication).microphone
            var lease: MicrophoneGate.Lease? = null
            var route: AudioRoute? = null
            val cpu = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PbtMetaGPT:escucha-local")
            try {
                check(WakeModel(context).installed())
                LibVosk.setLogLevel(LogLevel.WARNINGS) // Nunca emitir texto reconocido por logs nativos.
                Model(WakeModel(context).model.absolutePath).use { model ->
                    // Modelo español abierto: permite nombres de su vocabulario sin sesgar todo el ruido a una frase.
                    Recognizer(model, 16000f).use { recognizer ->
                        recognizer.setMaxAlternatives(5)
                        recognizer.setWords(true)
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                        lease = gatekeeper.acquire(MicrophoneOwner.CONTINUOUS_LOCAL)
                        check(lease != null) { "El micrófono ya está en uso" }
                        val bluetooth = context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false)
                        if (bluetooth) {
                            val captureJob = currentCoroutineContext().job
                            val requested = AudioRoute(context) {
                                captureJob.cancel()
                                scope.launch(Dispatchers.Main) { failed(IllegalStateException("Ruta de audio interrumpida")) }
                            }
                            route = requested; requested.start(true)
                        }
                        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        check(minimum > 0)
                        val recorder = AudioRecord(if (bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
                        try {
                            check(recorder.state == AudioRecord.STATE_INITIALIZED)
                            recorder.startRecording(); check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                            val capturedAt = android.os.SystemClock.elapsedRealtime()
                            cpu.acquire()
                            withContext(Dispatchers.Main) { ready() }
                            val buffer = ShortArray(3200)
                            var actualInput: Int? = null
                            while (isActive) {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                                if (!context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) throw SecurityException()
                                val mode = context.getSystemService(AudioManager::class.java).mode
                                check(mode != AudioManager.MODE_IN_CALL) { "Audio interrumpido" }
                                check(recorder.activeRecordingConfiguration?.isClientSilenced != true) { "Android ha silenciado la captura" }
                                recorder.routedDevice?.let { device ->
                                    if (actualInput != null && actualInput != device.id) throw IllegalStateException("La entrada ha cambiado")
                                    if (actualInput == null) {
                                        actualInput = device.id
                                        withContext(Dispatchers.Main) { input(device.type) }
                                    }
                                }
                                val size = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                                check(size >= 0) { "Captura interrumpida" }
                                if (size > 0 && recognizer.acceptWaveForm(buffer, size)) {
                                    val result = JSONObject(recognizer.result)
                                    val alternatives = result.optJSONArray("alternatives")
                                    val texts = if (alternatives == null) listOf(result.optString("text"))
                                        else (0 until alternatives.length()).map { alternatives.getJSONObject(it).optString("text") }
                                    val match = gate.acceptUtterance(texts, epoch)
                                    if (match != null) {
                                        alternatives?.optJSONObject(0)?.optJSONArray("result")?.optJSONObject(0)?.let { word ->
                                            if (word.has("start")) {
                                                val latency = (android.os.SystemClock.elapsedRealtime() - capturedAt - (word.optDouble("start") * 1000).toLong()).coerceAtLeast(0)
                                                withContext(Dispatchers.Main) { LatencyStore(context).record(mapOf(VoiceStage.WAKE_DETECTION to latency)) }
                                            }
                                        }
                                        // Primero salir y liberar AudioRecord; el callback se encola después del finally.
                                        val recordingJob = currentCoroutineContext().job
                                        scope.launch(Dispatchers.Main) { recordingJob.join(); detected(match.profile, match.question) }
                                        break
                                    }
                                }
                                buffer.fill(0)
                                delay(20)
                            }
                        } finally {
                            try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                            finally { recorder.release() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (problem: IOException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: IllegalStateException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: IllegalArgumentException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: SecurityException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: org.json.JSONException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: LinkageError) { withContext(Dispatchers.Main) { failed(problem) } }
            finally { if (cpu.isHeld) cpu.release(); route?.close(); gatekeeper.release(lease) }
        }
    }
}
