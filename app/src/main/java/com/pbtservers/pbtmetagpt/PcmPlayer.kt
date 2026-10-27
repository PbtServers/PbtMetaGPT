package com.pbtservers.pbtmetagpt

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class PcmPlayer {
    private var track: AudioTrack? = null
    suspend fun play(audio: PcmAudio) { playStream(flowOf(audio)) }
    suspend fun playStream(chunks: Flow<PcmAudio>, firstFrame: () -> Unit = {}): Unit = coroutineScope {
        var output: AudioTrack? = null
        var rate = 0; var totalFrames = 0L; var observing: Job? = null
        try {
            chunks.collect { audio ->
                require(audio.sampleRate in 8000..48000 && audio.bytes.isNotEmpty() && audio.bytes.size % 2 == 0)
                if (output == null) {
                    rate = audio.sampleRate
                    val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    require(minimum > 0)
                    output = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 2).build()
                    track = output
                }
                require(rate == audio.sampleRate)
                val current = requireNotNull(output)
                if (totalFrames == 0L) {
                    current.play()
                    observing = launch {
                        try {
                            withTimeoutOrNull(5000) { while (current.playbackHeadPosition == 0) delay(5); firstFrame() }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: IllegalStateException) { /* salida cancelada antes de la primera trama */ }
                    }
                }
                withContext(Dispatchers.IO) {
                    var offset = 0
                    while (offset < audio.bytes.size) {
                        ensureActive()
                        val written = current.write(audio.bytes, offset, minOf(8192, audio.bytes.size - offset))
                        check(written > 0); offset += written
                    }
                }
                totalFrames += audio.bytes.size / 2L
            }
            output?.let { current -> withTimeout(totalFrames * 1000 / rate + 3000) {
                while ((current.playbackHeadPosition.toLong() and 0xffffffffL) < totalFrames) delay(10)
            } }
        } finally {
            observing?.cancel()
            val current = output
            if (track === current) { track = null; runCatching { current?.stop() }; current?.release() }
        }
        Unit
    }
    fun stop() { val previous = track; track = null; runCatching { previous?.pause() }; runCatching { previous?.flush() }; runCatching { previous?.stop() }; runCatching { previous?.release() } }
}
