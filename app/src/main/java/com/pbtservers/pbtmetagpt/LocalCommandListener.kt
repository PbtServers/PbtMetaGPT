package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONObject
import org.vosk.*

/** Captura concurrente con TTS, nunca con otro capturador. No conserva audio ni texto. */
class LocalCommandListener(private val context: Context) {
    suspend fun run(name: String, options: ListeningOptions, speaking: () -> Boolean, echoText: () -> String,
        status: (String) -> Unit, command: (LocalVoiceCommand, Long) -> Unit) = withContext(Dispatchers.IO) {
        if (!options.interruption || !WakeModel(context).installed()) {
            withContext(Dispatchers.Main) { status("Interrupción por voz no disponible · utiliza Parar") }
            return@withContext
        }
        val gate = (context.applicationContext as PbtApplication).microphone
        val lease = gate.acquire(MicrophoneOwner.COMMAND_LOCAL)
        if (lease == null) {
            withContext(Dispatchers.Main) { status("Micrófono ocupado · utiliza Parar") }
            return@withContext
        }
        var record: AudioRecord? = null
        var aec: AcousticEchoCanceler? = null
        val buffer = ShortArray(320) // 20 ms; sin asignaciones de PCM por lectura.
        try {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(WakeModel(context).model.absolutePath).use { model ->
                // Gramática abierta: una gramática de una palabra puede forzar ruido/frases a «para».
                Recognizer(model, 16000f).use { recognizer ->
                    recognizer.setWords(true)
                    recognizer.setEndpointerMode(1) // SHORT, contrato público Vosk.
                    check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                    val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    check(minimum > 0)
                    val active = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, 16000, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 6400)).also { record = it }
                    check(active.state == AudioRecord.STATE_INITIALIZED)
                    if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(active.audioSessionId)?.also { it.enabled = true }
                    active.startRecording()
                    check(active.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                    val protection = SystemClock.elapsedRealtime() + 350
                    val started = SystemClock.elapsedRealtime()
                    val filter = InterruptionGate()
                    var routeId: Int? = null
                    var announced = false
                    while (isActive) {
                        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
                        check(active.activeRecordingConfiguration?.isClientSilenced != true)
                        check(context.getSystemService(AudioManager::class.java).mode != AudioManager.MODE_IN_CALL)
                        val routed = active.routedDevice
                        if (routed != null) {
                            if (routeId != null && routeId != routed.id) throw IllegalStateException()
                            routeId = routed.id
                            if (!announced) {
                                announced = true
                                val source = if (routed.type == AudioDeviceInfo.TYPE_BUILTIN_MIC) "teléfono" else "entrada Android tipo ${routed.type}"
                                withContext(Dispatchers.Main) { status("Detector local · $source · barge-in experimental${if (aec?.enabled != true) " · sin AEC: Parar durante voz" else ""}") }
                            }
                        }
                        val n = active.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                        check(n >= 0)
                        if (n > 0 && recognizer.acceptWaveForm(buffer, n)) {
                            val result = JSONObject(recognizer.result)
                            val text = result.optString("text")
                            val words = result.optJSONArray("result")
                            val confidence = if (words == null || words.length() == 0) 0f else (0 until words.length())
                                .minOf { words.getJSONObject(it).optDouble("conf", 0.0) }.toFloat()
                            val output = VoiceCommands.normalize(echoText())
                            val heard = VoiceCommands.normalize(text)
                            val echoRisk = speaking() && heard.isNotEmpty() && " $output ".contains(" $heard ")
                            val now = SystemClock.elapsedRealtime()
                            val accepted = filter.accept(text, confidence, now, protection,
                                echoSafe = routed != null && (!speaking() || aec?.enabled == true) && !echoRisk, name, options)
                            if (accepted != null) {
                                val onset = words?.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0
                                val detection = (now - started - (onset * 1000).toLong()).coerceAtLeast(0)
                                withContext(Dispatchers.Main) { command(accepted, detection) }
                                break
                            }
                        }
                        buffer.fill(0); delay(10)
                    }
                }
            }
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { withContext(Dispatchers.Main) { status("Captura de órdenes no disponible · utiliza Parar") } }
        catch (_: LinkageError) { withContext(Dispatchers.Main) { status("Motor local no disponible · utiliza Parar") } }
        finally {
            buffer.fill(0)
            runCatching { record?.stop() }; runCatching { aec?.release() }; runCatching { record?.release() }
            gate.release(lease)
        }
    }
}
