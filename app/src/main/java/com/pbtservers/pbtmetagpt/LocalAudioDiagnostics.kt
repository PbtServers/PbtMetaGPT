package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*
import kotlinx.coroutines.flow.update
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.json.JSONObject

data class MicrophoneTestState(val level: Float = 0f, val transcript: String = "", val route: String = "Entrada sin confirmar", val status: String = "Prueba local de hasta 12 segundos")

/** Pruebas iniciadas por el usuario. No guardan ni envían muestras a la red. */
class LocalAudioDiagnostics(private val context: Context, private val scope: CoroutineScope, private val diagnostics: Diagnostics) {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private var job: Job? = null
    private var toneAwaitingConfirmation = false
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var microphoneLease: MicrophoneGate.Lease? = null
    private val route = AudioRoute(context) { stop() }
    private val mutableTest = MutableStateFlow(MicrophoneTestState())
    val microphoneTest = mutableTest.asStateFlow()

    fun microphone(bluetooth: Boolean) = start(Check.LOCAL_MIC, bluetooth) {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        check(WakeModel(context).installed()) { "Instala Vosk español antes de probar la transcripción local." }
        mutableTest.value = MicrophoneTestState(status = "Habla ahora. No se guarda ni envía la grabación.")
        val size = maxOf(3200, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(size).build()
        record = recorder
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            AudioInputSettings(context).prefer(recorder)
            recorder.startRecording()
            val buffer = ShortArray(1600)
            withContext(Dispatchers.IO) {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                Model(WakeModel(context).model.absolutePath).use { model ->
                Recognizer(model, 16000f).use { recognizer ->
                val until = android.os.SystemClock.elapsedRealtime() + 12_000
                var samples = 0
                var energy = 0.0
                var finalText = ""
                while (android.os.SystemClock.elapsedRealtime() < until) {
                    ensureActive()
                    val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    check(count > 0)
                    check(!recorder.activeRecordingConfiguration.let { it?.isClientSilenced == true })
                    val actual = recorder.routedDevice
                    val valid = (context.applicationContext as PbtApplication).audioSession.report(actual, recorder.sampleRate)
                    mutableTest.update { it.copy(route = AudioInputSettings.describe(actual)) }
                    if (!valid) { buffer.fill(0); continue }
                    samples += count
                    var frameEnergy = 0.0
                    for (i in 0 until count) frameEnergy += buffer[i].toDouble() * buffer[i]
                    energy += frameEnergy
                    val final = recognizer.acceptWaveForm(buffer, count)
                    val text = JSONObject(if (final) recognizer.result else recognizer.partialResult).optString(if (final) "text" else "partial")
                    if (final && text.isNotBlank()) finalText = "$finalText $text".trim().takeLast(500)
                    mutableTest.update { it.copy(level = (sqrt(frameEnergy / count) / 5000).toFloat().coerceIn(0f, 1f),
                        transcript = if (final) finalText else "$finalText $text".trim().takeLast(500)) }
                    buffer.fill(0)
                }
                val last = JSONObject(recognizer.finalResult).optString("text")
                mutableTest.update { it.copy(level = 0f, transcript = "$finalText $last".trim().takeLast(500), status = "Prueba finalizada. Compara el nombre de entrada con tus gafas; la transcripción se borra al salir.") }
                val expectedBluetooth = AudioInputSettings(context).useBluetooth(bluetooth)
                val expected = AudioInputSettings(context).accepts(recorder.routedDevice, expectedBluetooth)
                val rms = if (samples == 0) 0 else sqrt(energy / samples).toInt()
                diagnostics.record(Check.LOCAL_MIC, if (samples > 0 && rms > 80 && expected) CheckStatus.OK else CheckStatus.WARNING,
                    "Prueba PCM16 mono 16000 Hz: $samples muestras; RMS $rms; ${deviceType(recorder.routedDevice?.type)}. La identidad y comprensión las debe confirmar el usuario; no se conserva texto.", if (expected) "SAMPLES_READ" else "ROUTE_MISMATCH")
                } }
            }
        } finally { runCatching { recorder.stop() }; recorder.release(); record = null }
    }

    fun speaker(bluetooth: Boolean) = start(Check.LOCAL_SPEAKER, bluetooth) {
        toneAwaitingConfirmation = false
        val rate = 16000
        val samples = ShortArray(rate) { (sin(2.0 * PI * 440 * it / rate) * 1800).toInt().toShort() }
        val player = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(samples.size * 2).build()
        track = player
        try {
            check(player.state == AudioTrack.STATE_INITIALIZED)
            check(player.write(samples, 0, samples.size) == samples.size)
            player.play()
            delay(1100)
            val routed = deviceType(player.routedDevice?.type)
            toneAwaitingConfirmation = true
            diagnostics.record(Check.LOCAL_SPEAKER, CheckStatus.WARNING, "Se ha reproducido un tono de un segundo. Salida informada: $routed. Confirma si lo has oído y en qué dispositivo.", "TONE_SUBMITTED")
        } finally { runCatching { player.stop() }; player.release(); track = null }
    }

    fun confirmSpeaker(heard: Boolean) {
        if (!toneAwaitingConfirmation || busy.value) return
        toneAwaitingConfirmation = false
        diagnostics.record(Check.LOCAL_SPEAKER, if (heard) CheckStatus.OK else CheckStatus.ERROR,
            if (heard) "El usuario confirma haber oído el tono en el dispositivo probado. No acredita salida de audio de OpenAI." else "El usuario no ha oído el tono. Comprueba volumen y ruta de salida.", "USER_CONFIRMATION", manual = true)
    }
    private fun start(check: Check, bluetooth: Boolean, action: suspend () -> Unit) {
        if (busy.value) return
        mutableBusy.value = true
        diagnostics.record(check, CheckStatus.RUNNING, "Prueba local iniciada expresamente; no se envía audio.")
        job = scope.launch {
            try {
                val gate = (context.applicationContext as? PbtApplication)?.microphone
                if (gate != null) {
                    withTimeout(5000) { while (gate.owner() != null) delay(20) }
                    microphoneLease = gate.acquire(MicrophoneOwner.DIAGNOSTIC)
                    check(microphoneLease != null) { "Micrófono ocupado por otro motor" }
                }
                route.start(bluetooth, speaker = true)
                diagnostics.record(Check.ROUTE, CheckStatus.WARNING, if (bluetooth) "Ruta Bluetooth solicitada; la prueba informa del tipo realmente observado." else "Altavoz y micrófono del teléfono solicitados para la prueba local.")
                withTimeout(20_000) { action() }
            } catch (_: TimeoutCancellationException) { diagnostics.record(check, CheckStatus.ERROR, "Tiempo agotado en la prueba local.", "LOCAL_TIMEOUT")
            } catch (cancelled: CancellationException) { diagnostics.record(check, CheckStatus.WARNING, "Prueba local cancelada.", "CANCELLED"); throw cancelled
            } catch (problem: IllegalStateException) { failure(check, problem) }
            catch (problem: SecurityException) { failure(check, problem) }
            catch (problem: IllegalArgumentException) { failure(check, problem) }
            finally {
                try { route.close() }
                finally { (context.applicationContext as? PbtApplication)?.microphone?.release(microphoneLease); microphoneLease = null; mutableBusy.value = false }
            }
        }
    }
    fun stop() {
        job?.cancel()
        mutableTest.value = MicrophoneTestState()
        // Desbloquea una lectura nativa antes de que la corrutina libere el AudioRecord.
        runCatching { record?.stop() }
        runCatching { track?.stop() }
        toneAwaitingConfirmation = false
    }
    private fun failure(check: Check, problem: Throwable) {
        mutableTest.update { it.copy(level = 0f, status = if (!WakeModel(context).installed()) "Instala el modelo Vosk español en Activación por voz antes de probar." else "Prueba interrumpida. Revisa permisos y micrófono seleccionado.") }
        (context.applicationContext as? PbtApplication)?.journal?.record(check, Fault.INITIALIZATION, problem)
        diagnostics.record(check, CheckStatus.ERROR, "No se pudo completar la prueba. Revisa permisos, ruta y aplicaciones que usan audio.", "LOCAL_AUDIO_ERROR")
    }
    private fun deviceType(type: Int?): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micrófono del teléfono"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "altavoz del teléfono"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "auricular del teléfono"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "auricular Bluetooth LE"
        null -> "ruta no confirmada"
        else -> "otro dispositivo Android (tipo $type)"
    }
}
