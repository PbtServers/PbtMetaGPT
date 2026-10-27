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
    suspend fun start(profiles: List<AssistantProfile>, ready: () -> Unit, detected: (AssistantProfile) -> Unit, failed: (Throwable) -> Unit) {
        stop()
        val gate = ActivationGate(); val epoch = gate.arm(profiles)
        work = scope.launch(Dispatchers.IO) {
            val gatekeeper = (context.applicationContext as PbtApplication).microphone
            var lease: MicrophoneGate.Lease? = null
            val cpu = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PbtMetaGPT:escucha-local")
            try {
                check(WakeModel(context).installed())
                LibVosk.setLogLevel(LogLevel.WARNINGS) // Nunca emitir texto reconocido por logs nativos.
                Model(WakeModel(context).model.absolutePath).use { model ->
                    // Modelo español abierto: permite nombres de su vocabulario sin sesgar todo el ruido a una frase.
                    Recognizer(model, 16000f).use { recognizer ->
                        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                        lease = gatekeeper.acquire(MicrophoneOwner.CONTINUOUS_LOCAL)
                        check(lease != null) { "El micrófono ya está en uso" }
                        val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                        check(minimum > 0)
                        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, 16000,
                            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400))
                        try {
                            check(recorder.state == AudioRecord.STATE_INITIALIZED)
                            recorder.startRecording(); check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                            cpu.acquire()
                            withContext(Dispatchers.Main) { ready() }
                            val buffer = ShortArray(3200)
                            while (isActive) {
                                if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                                if (!context.getSystemService(android.app.NotificationManager::class.java).areNotificationsEnabled()) throw SecurityException()
                                val mode = context.getSystemService(AudioManager::class.java).mode
                                check(mode != AudioManager.MODE_IN_CALL && mode != AudioManager.MODE_IN_COMMUNICATION) { "Audio interrumpido" }
                                check(recorder.activeRecordingConfiguration?.isClientSilenced != true) { "Android ha silenciado la captura" }
                                val size = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                                check(size >= 0) { "Captura interrumpida" }
                                if (size > 0 && recognizer.acceptWaveForm(buffer, size)) {
                                    val text = JSONObject(recognizer.result).optString("text")
                                    val match = gate.accept(text, epoch)
                                    if (match != null) {
                                        // Primero salir y liberar AudioRecord; el callback se encola después del finally.
                                        val recordingJob = currentCoroutineContext().job
                                        scope.launch(Dispatchers.Main) { recordingJob.join(); detected(match) }
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
            catch (problem: SecurityException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: org.json.JSONException) { withContext(Dispatchers.Main) { failed(problem) } }
            catch (problem: LinkageError) { withContext(Dispatchers.Main) { failed(problem) } }
            finally { if (cpu.isHeld) cpu.release(); gatekeeper.release(lease) }
        }
    }
}
