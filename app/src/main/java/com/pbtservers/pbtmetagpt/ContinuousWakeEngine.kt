package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.*
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import java.io.IOException

/** ASR local continuo. No usa SpeechRecognizer, no guarda PCM ni transcripciones. */
class ContinuousWakeEngine(private val context: Context, private val scope: CoroutineScope) {
    private var work: Job? = null
    suspend fun stop() { work?.cancelAndJoin(); work = null }
    suspend fun start(profiles: List<AssistantProfile>, ready: () -> Unit, detected: (AssistantProfile, String) -> Unit, failed: (Throwable) -> Unit, input: (Int) -> Unit = {}) {
        stop()
        val gate = ActivationGate(); val epoch = gate.arm(profiles)
        work = scope.launch(Dispatchers.IO) {
            val gatekeeper = (context.applicationContext as PbtApplication).microphone
            var lease: MicrophoneGate.Lease? = null
            var route: AudioRoute? = null
            try {
                check(WakeModel(context).installed())
                LibVosk.setLogLevel(LogLevel.WARNINGS) // Nunca emitir texto reconocido por logs nativos.
                (context.applicationContext as PbtApplication).voskModels.acquire(WakeModel(context).model.absolutePath).use { model ->
                    // Modelo español abierto: permite nombres de su vocabulario sin sesgar todo el ruido a una frase.
                    PassiveWakeDecoder(model.model).use { recognizer ->
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                        lease = gatekeeper.acquire(MicrophoneOwner.CONTINUOUS_LOCAL)
                        check(lease != null) { "El micrófono ya está en uso" }
                        val bluetooth = AudioInputSettings(context).useBluetooth()
                        run {
                            val captureJob = currentCoroutineContext().job
                            val requested = AudioRoute(context) {
                                captureJob.cancel()
                                scope.launch(Dispatchers.Main) { failed(IllegalStateException("Ruta de audio interrumpida")) }
                            }
                            route = requested; requested.start(bluetooth, speaker = true, passive = true)
                        }
                        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        check(minimum > 0)
                        val recorder = AudioRecord(if (bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
                        try {
                            check(recorder.state == AudioRecord.STATE_INITIALIZED)
                            AudioInputSettings(context).prefer(recorder)
                            recorder.startRecording(); check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                            val recordingAt = android.os.SystemClock.elapsedRealtime()
                            var capturedAt = recordingAt
                            val buffer = ShortArray(320)
                            var announcedReady = false
                            var actualInput: Int? = null
                            var nextSystemCheck = 0L
                            while (isActive) {
                                val now = android.os.SystemClock.elapsedRealtime()
                                if (now >= nextSystemCheck) {
                                    nextSystemCheck = now + 1000
                                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                                    if (!context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) throw SecurityException()
                                    check(context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_IN_CALL) { "Audio interrumpido" }
                                    check(recorder.activeRecordingConfiguration?.isClientSilenced != true) { "Android ha silenciado la captura" }
                                }
                                recorder.routedDevice?.let { device ->
                                    if (actualInput != null && actualInput != device.id) throw IllegalStateException("La entrada ha cambiado")
                                    if (actualInput == null) {
                                        actualInput = device.id
                                        withContext(Dispatchers.Main) { input(device.type) }
                                    }
                                }
                                val size = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                                check(size >= 0) { "Captura interrumpida" }
                                val validInput = (context.applicationContext as PbtApplication).audioSession.report(recorder.routedDevice, recorder.sampleRate)
                                if (!announcedReady && size > 0 && validInput) {
                                    announcedReady = true
                                    capturedAt = android.os.SystemClock.elapsedRealtime() - size * 1000L / 16000
                                    withContext(Dispatchers.Main) { ready() }
                                }
                                check(announcedReady || android.os.SystemClock.elapsedRealtime() - recordingAt < 5000) { "Entrada no confirmada" }
                                val result = if (size > 0 && validInput) recognizer.accept(buffer, size) else null
                                if (result != null) {
                                    val alternatives = result.optJSONArray("alternatives")
                                    val texts = if (alternatives == null) listOf(result.optString("text"))
                                        else (0 until alternatives.length()).map { alternatives.getJSONObject(it).optString("text") }
                                    val match = gate.acceptUtterance(texts, epoch)
                                    if (match != null) {
                                        alternatives?.optJSONObject(0)?.optJSONArray("result")?.optJSONObject(0)?.let { word ->
                                            if (word.has("start")) {
                                                val latency = (android.os.SystemClock.elapsedRealtime() - capturedAt - recognizer.startFrame * 1000 / 16000 - (word.optDouble("start") * 1000).toLong()).coerceAtLeast(0)
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
                                if (size == 0) delay(10)
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
            finally { route?.close(); gatekeeper.release(lease) }
        }
    }
}
