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
