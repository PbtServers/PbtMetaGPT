package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Captura acotada de una sola petición; solo el usuario puede habilitar su envío a Groq.
 * No crea archivos ni conserva la voz después de la transcripción.
 */
class GroqAudioCapture(private val context: Context) {
    suspend fun capture(speechEnded: () -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw HostedFailure("Falta permiso de micrófono para dictar.")
        val gate = (context.applicationContext as PbtApplication).microphone
        val lease = gate.acquire(MicrophoneOwner.HOSTED_LOCAL)
            ?: throw HostedFailure("Micrófono ocupado. Detén la otra escucha.")
        val route = AudioRoute(context) {}
        try {
            val bluetooth = context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false)
            if (bluetooth) route.start(true)
            val rate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0)
            val recorder = AudioRecord(if (bluetooth) MediaRecorder.AudioSource.VOICE_COMMUNICATION else MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 6400))
            val samples = ShortArray(320)
            val preRoll = PcmPreRoll()
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val vad = AdaptiveVad(rate)
                val pcm = ByteArrayOutputStream(300000)
                var totalFrames = 0
                val maxFrames = rate * 12
                while (totalFrames < maxFrames) {
                    ensureActive()
                    check(recorder.activeRecordingConfiguration?.isClientSilenced != true)
                    val read = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) throw HostedFailure("No se pudo leer audio del micrófono.")
                    val previouslyStarted = vad.started
                    if (!previouslyStarted) preRoll.append(samples, read)
                    val event = vad.accept(samples, read)
                    val selected = if (!previouslyStarted && vad.started) preRoll.snapshot() else if (previouslyStarted) samples.copyOf(read) else shortArrayOf()
                    for (sampleValue in selected) {
                        val sample = sampleValue.toInt()
                        pcm.write(sample and 255)
                        pcm.write((sample shr 8) and 255)
                    }
                    selected.fill(0)
                    if (vad.started) preRoll.clear()
                    totalFrames += read
                    if (event == AdaptiveVad.Event.END || !vad.started && totalFrames >= rate * 6) break
                }
                samples.fill(0)
                preRoll.clear()
                if (!vad.enoughVoice()) throw HostedFailure("No se detectó suficiente voz. Vuelve a hablar tras la confirmación.")
                speechEnded()
                WavAudio.encodePcm16Mono(rate, pcm.toByteArray())
            } finally {
                samples.fill(0); preRoll.clear()
                try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                finally { recorder.release() }
            }
        } catch (_: SecurityException) {
            throw HostedFailure("Android no permite usar el micrófono. Comprueba el permiso.")
        } finally { route.close(); gate.release(lease) }
    }
}

/** WAV PCM mono, sin metadatos ni identidad; tamaño limitado por la captura de 12 s. */
object WavAudio {
    fun encodePcm16Mono(rate: Int, pcm: ByteArray): ByteArray {
        require(rate == 16000 && pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= 400_000)
        val out = ByteArrayOutputStream(pcm.size + 44)
        fun text(value: String) { out.write(value.toByteArray(Charsets.US_ASCII)) }
        fun word(value: Int) { out.write(value and 255); out.write((value ushr 8) and 255) }
        fun dword(value: Int) { word(value and 65535); word(value ushr 16) }
        text("RIFF"); dword(36 + pcm.size); text("WAVE"); text("fmt "); dword(16)
        word(1); word(1); dword(rate); dword(rate * 2); word(2); word(16)
        text("data"); dword(pcm.size); out.write(pcm)
        return out.toByteArray()
    }
}
