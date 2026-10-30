package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.SystemClock
import kotlinx.coroutines.*

/** Única captura PCM para dictado Vosk/Whisper: prebuffer, ruta verificada y plazos separados. */
class PcmSpeechCapture(private val context: Context) {
    suspend fun capture(continuous: Boolean, silenceMs: Int, ready: () -> Unit, started: () -> Unit,
        ended: () -> Unit, consume: (ShortArray, Int) -> Unit) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as PbtApplication
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw HostedFailure("Falta permiso de micrófono.")
        val lease = app.microphone.acquire(MicrophoneOwner.HOSTED_LOCAL)
            ?: throw HostedFailure("Micrófono ocupado. Detén la otra escucha y reintenta.")
        val captureJob = currentCoroutineContext().job
        val route = AudioRoute(context) { captureJob.cancel() }
        var recorder: AudioRecord? = null
        val buffer = ShortArray(320)
        val preRoll = PcmPreRoll()
        try {
            val bluetooth = AudioInputSettings(context).useBluetooth()
            route.start(bluetooth, speaker = !bluetooth)
            val minimum = AudioRecord.getMinBufferSize(16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            val record = AudioRecord(if (bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION,
                16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minimum, 12800))
            recorder = record
            check(record.state == AudioRecord.STATE_INITIALIZED)
            AudioInputSettings(context).prefer(record)
            record.startRecording()
            check(record.recordingState == AudioRecord.RECORDSTATE_RECORDING)
            val vad = AdaptiveVad(endSilenceMs = silenceMs)
            val opened = SystemClock.elapsedRealtime()
            var announced = false
            var speechAt = 0L
            var nextCheck = 0L
            while (true) {
                ensureActive()
                val now = SystemClock.elapsedRealtime()
                if (now >= nextCheck) {
                    nextCheck = now + 1000
                    if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) throw SecurityException()
                    if (record.activeRecordingConfiguration?.isClientSilenced == true || context.getSystemService(AudioManager::class.java).mode == AudioManager.MODE_IN_CALL)
                        throw HostedFailure("Android ha silenciado el micrófono o hay una llamada. Reanuda la escucha cuando termine.")
                }
                if (!announced && now - opened >= 5000) throw HostedFailure("No se ha confirmado la entrada de audio. Comprueba el micrófono seleccionado.")
                if (speechAt > 0 && now - speechAt >= 28000) break
                if (speechAt == 0L && now - opened >= (if (continuous) 60000 else 20000)) throw NoSpeechDetected()
                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                if (count < 0) throw HostedFailure("Se ha interrumpido la captura. Comprueba el micrófono y reintenta.")
                if (count == 0) { delay(10); continue }
                if (!app.audioSession.report(record.routedDevice, record.sampleRate)) { buffer.fill(0); delay(10); continue }
                if (!announced) { announced = true; withContext(Dispatchers.Main) { ready() } }
                val previous = vad.started
                if (!previous) preRoll.append(buffer, count)
                val event = vad.accept(buffer, count)
                if (!previous && vad.started) {
                    speechAt = now
                    withContext(Dispatchers.Main) { started() }
                    val prefix = preRoll.snapshot()
                    try { consume(prefix, prefix.size) } finally { prefix.fill(0); preRoll.clear() }
                } else if (previous) consume(buffer, count)
                buffer.fill(0)
                if (event == AdaptiveVad.Event.END) break
                // Sin delay por trama: vaciar el PCM pendiente antes de esperar más audio.
            }
            if (!vad.enoughVoice()) throw NoSpeechDetected()
            withContext(Dispatchers.Main) { ended() }
        } catch (_: SecurityException) { throw HostedFailure("Android ha denegado el micrófono. Revisa su permiso y vuelve a activar la escucha.") }
        finally {
            buffer.fill(0); preRoll.clear()
            try { recorder?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() } }
            finally { try { recorder?.release() } finally { route.close(); app.microphone.release(lease) } }
        }
    }
}
