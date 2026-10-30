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
    suspend fun capture(speechEnded: () -> Unit = {}, continuous: Boolean = false, ready: () -> Unit = {},
        speechStarted: () -> Unit = {}, silenceMs: Int = 1700): ByteArray = withContext(Dispatchers.IO) {
        // Se borra también el búfer interno, no solo la copia entregada al proveedor.
        val pcm = object : ByteArrayOutputStream(65536) { fun erase() { buf.fill(0); reset() } }
        try {
            PcmSpeechCapture(context).capture(continuous, silenceMs, ready, speechStarted, speechEnded) { samples, count ->
                for (i in 0 until count) {
                    pcm.write(samples[i].toInt() and 255)
                    pcm.write((samples[i].toInt() shr 8) and 255)
                }
            }
            val bytes = pcm.toByteArray()
            try { WavAudio.encodePcm16Mono(16000, bytes) } finally { bytes.fill(0) }
        } finally { pcm.erase() }
    }
}

/** WAV PCM mono, sin metadatos ni identidad; tamaño limitado por la captura de 12 s. */
object WavAudio {
    fun encodePcm16Mono(rate: Int, pcm: ByteArray): ByteArray {
        require(rate == 16000 && pcm.isNotEmpty() && pcm.size % 2 == 0 && pcm.size <= 960_000)
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
