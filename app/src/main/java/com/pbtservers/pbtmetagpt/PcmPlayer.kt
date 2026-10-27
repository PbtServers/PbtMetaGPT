package com.pbtservers.pbtmetagpt

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*

class PcmPlayer {
    private var track: AudioTrack? = null
    suspend fun play(audio: PcmAudio) {
        require(audio.sampleRate in 8000..48000 && audio.bytes.isNotEmpty() && audio.bytes.size % 2 == 0)
        val minimum = AudioTrack.getMinBufferSize(audio.sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        require(minimum > 0)
        val output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 4).build()
        track = output
        try {
            output.play()
            withContext(Dispatchers.IO) {
                var offset = 0
                while (offset < audio.bytes.size) {
                    ensureActive()
                    val written = output.write(audio.bytes, offset, minOf(8192, audio.bytes.size - offset))
                    check(written > 0) { "La salida de audio se ha detenido." }; offset += written
                }
            }
            withTimeout(audio.bytes.size * 1000L / (2 * audio.sampleRate) + 3000) {
                while (output.playbackHeadPosition.toLong() < audio.bytes.size / 2L) delay(20)
            }
        } finally { if (track === output) { track = null; runCatching { output.stop() }; output.release() } }
    }
    fun stop() { val previous = track; track = null; runCatching { previous?.pause() }; runCatching { previous?.flush() }; runCatching { previous?.stop() }; runCatching { previous?.release() } }
}
