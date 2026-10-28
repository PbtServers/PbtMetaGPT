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
import kotlin.math.abs

/**
 * Captura acotada de una sola petición; solo el usuario puede habilitar su envío a Groq.
 * No crea archivos ni conserva la voz después de la transcripción.
 */
class GroqAudioCapture(private val context: Context) {
    suspend fun capture(): ByteArray = withContext(Dispatchers.IO) {
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            throw HostedFailure("Falta permiso de micrófono para dictar.")
        val gate = (context.applicationContext as PbtApplication).microphone
        val lease = gate.acquire(MicrophoneOwner.HOSTED_LOCAL)
            ?: throw HostedFailure("Micrófono ocupado. Detén la otra escucha.")
        try {
            val rate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minBuffer > 0)
            val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, 6400))
            try {
                check(recorder.state == AudioRecord.STATE_INITIALIZED)
                recorder.startRecording()
                check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING)
                val samples = ShortArray(3200)
                val pcm = ByteArrayOutputStream(300000)
                var totalFrames = 0
                var voicedFrames = 0
                var silenceFrames = 0
                val maxFrames = rate * 12
                while (totalFrames < maxFrames) {
                    ensureActive()
                    check(recorder.activeRecordingConfiguration?.isClientSilenced != true)
                    val read = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (read <= 0) throw HostedFailure("No se pudo leer audio del micrófono.")
                    for (i in 0 until read) {
                        val sample = samples[i].toInt()
                        pcm.write(sample and 255)
                        pcm.write((sample shr 8) and 255)
                    }
                    val loud = (0 until read).count { abs(samples[it].toInt()) > 450 }
                    if (loud > read / 30) {
                        voicedFrames += read
                        silenceFrames = 0
                    } else if (voicedFrames > rate / 3) silenceFrames += read
                    totalFrames += read
                    if (voicedFrames > rate / 3 && silenceFrames > rate) break
                }
                samples.fill(0)
                if (voicedFrames < rate / 6) throw HostedFailure("No se detectó suficiente voz. Vuelve a hablar tras la confirmación.")
                WavAudio.encodePcm16Mono(rate, pcm.toByteArray())
            } finally {
                try { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                finally { recorder.release() }
            }
        } catch (_: SecurityException) {
            throw HostedFailure("Android no permite usar el micrófono. Comprueba el permiso.")
        } finally { gate.release(lease) }
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
