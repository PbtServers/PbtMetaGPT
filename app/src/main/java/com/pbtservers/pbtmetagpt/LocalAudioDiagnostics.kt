package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

/** Pruebas iniciadas por el usuario. No guardan ni envían muestras a la red. */
class LocalAudioDiagnostics(private val context: Context, private val scope: CoroutineScope, private val diagnostics: Diagnostics) {
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private var job: Job? = null
    private var toneAwaitingConfirmation = false
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private val route = AudioRoute(context) { stop() }

    fun microphone(bluetooth: Boolean) = start(Check.LOCAL_MIC, bluetooth) {
        check(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
        val size = maxOf(3200, AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT))
        val recorder = AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(16000).setChannelMask(AudioFormat.CHANNEL_IN_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(size).build()
        record = recorder
        try {
            check(recorder.state == AudioRecord.STATE_INITIALIZED)
            recorder.startRecording()
            val buffer = ShortArray(1600)
            var samples = 0
            var energy = 0.0
            withContext(Dispatchers.IO) {
                repeat(30) {
                    ensureActive()
                    val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                    check(count > 0)
                    samples += count
                    for (i in 0 until count) energy += buffer[i].toDouble() * buffer[i]
                }
            }
            val rms = sqrt(energy / samples)
            val routed = recorder.routedDevice
            val routeText = deviceType(routed?.type)
            val expected = if (bluetooth) routed?.type in listOf(AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET)
                else routed?.type == AudioDeviceInfo.TYPE_BUILTIN_MIC
            diagnostics.record(Check.LOCAL_MIC, if (rms > 80 && expected) CheckStatus.OK else CheckStatus.WARNING,
                "Se han leído $samples muestras locales; nivel RMS ${rms.toInt()}; entrada: $routeText. Esto no verifica que la voz sea inteligible ni que proceda de unas Ray-Ban.", if (expected) "SAMPLES_READ" else "ROUTE_MISMATCH")
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
                route.start(bluetooth, speaker = true)
                diagnostics.record(Check.ROUTE, CheckStatus.WARNING, if (bluetooth) "Ruta Bluetooth solicitada; la prueba informa del tipo realmente observado." else "Altavoz y micrófono del teléfono solicitados para la prueba local.")
                withTimeout(7000) { action() }
            } catch (_: TimeoutCancellationException) { diagnostics.record(check, CheckStatus.ERROR, "Tiempo agotado en la prueba local.", "LOCAL_TIMEOUT")
            } catch (cancelled: CancellationException) { diagnostics.record(check, CheckStatus.WARNING, "Prueba local cancelada.", "CANCELLED"); throw cancelled
            } catch (_: Exception) { diagnostics.record(check, CheckStatus.ERROR, "No se pudo completar la prueba. Revisa permisos, ruta, volumen y aplicaciones que usan audio.", "LOCAL_AUDIO_ERROR")
            } finally { route.close(); mutableBusy.value = false }
        }
    }
    fun stop() {
        job?.cancel()
        // Desbloquea una lectura nativa antes de que la corrutina libere el AudioRecord.
        runCatching { record?.stop() }
        runCatching { track?.stop() }
        toneAwaitingConfirmation = false
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
