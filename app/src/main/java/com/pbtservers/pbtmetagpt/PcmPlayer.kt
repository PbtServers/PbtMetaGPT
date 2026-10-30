package com.pbtservers.pbtmetagpt

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf

/** Punto de sustitución interno para instrumentación; sin Intent ni entrada externa. */
internal interface PcmOutput {
    fun prepare(startFrames: Int) = Unit
    fun play()
    fun head(): Long
    fun write(bytes: ByteArray, offset: Int, count: Int): Int
    fun close()
}

class PcmPlayer internal constructor(private val outputFactory: ((Int) -> PcmOutput)? = null) {
    private val ownerLock = Any()
    private var revision = 0L
    private var track: PcmOutput? = null

    /** Una escritura no bloqueante y la liberación nunca acceden a la vez al puntero JNI. */
    internal class Output(private val native: AudioTrack) : PcmOutput {
        private var closed = false
        @Synchronized override fun prepare(startFrames: Int) {
            checkOpen()
            // API 31+, también para avisos inferiores a la capacidad del búfer.
            native.setStartThresholdInFrames(startFrames)
        }
        @Synchronized override fun play() { checkOpen(); native.play() }
        @Synchronized override fun head(): Long { checkOpen(); return native.playbackHeadPosition.toLong() and 0xffffffffL }
        @Synchronized override fun write(bytes: ByteArray, offset: Int, count: Int): Int {
            checkOpen()
            return native.write(bytes, offset, count, AudioTrack.WRITE_NON_BLOCKING)
        }
        private fun checkOpen() { if (closed) throw CancellationException("Reproducción interrumpida") }
        @Synchronized override fun close() {
            if (closed) return
            closed = true
            runCatching { native.pause() }; runCatching { native.flush() }
            runCatching { native.stop() }; runCatching { native.release() }
        }
    }
    suspend fun play(audio: PcmAudio) { playStream(flowOf(audio)) }
    suspend fun playStream(chunks: Flow<PcmAudio>, firstFrame: () -> Unit = {}, preRollMs: Int = 0): Unit = coroutineScope {
        require(preRollMs in 0..500)
        ensureActive()
        val token = synchronized(ownerLock) { revision }
        var output: PcmOutput? = null
        var rate = 0; var totalFrames = 0L; var preRollFrames = 0; var observing: Job? = null
        try {
            chunks.collect { audio ->
                require(audio.sampleRate in 8000..48000 && audio.bytes.isNotEmpty() && audio.bytes.size % 2 == 0)
                if (output == null) {
                    rate = audio.sampleRate
                    val created = outputFactory?.invoke(rate) ?: run {
                    val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
                    require(minimum > 0)
                    Output(AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                        .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                        .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(minimum * 2).build()) }
                    output = created
                    synchronized(ownerLock) {
                        if (token != revision) throw CancellationException("Reproducción interrumpida antes de abrir salida")
                        check(track == null) { "Ya hay una salida PCM activa" }
                        track = created
                    }
                }
                require(rate == audio.sampleRate)
                val current = requireNotNull(output)
                if (totalFrames == 0L) {
                    current.prepare(minOf(audio.bytes.size / 2, rate / 50).coerceAtLeast(1))
                    current.play()
                    // En Bluetooth la ruta puede estar despertando tras el aviso de Meta:
                    // alimentar silencio antes de la primera sílaba evita perder audio útil.
                    if (preRollMs > 0) {
                        preRollFrames = rate * preRollMs / 1000
                        val silence = ByteArray(preRollFrames * 2)
                        withContext(Dispatchers.IO) {
                            var offset = 0
                            var progressed = System.nanoTime()
                            while (offset < silence.size) {
                                ensureActive()
                                val written = current.write(silence, offset, minOf(8192, silence.size - offset))
                                check(written >= 0)
                                if (written == 0) {
                                    check(System.nanoTime() - progressed < 3_000_000_000L) { "La salida PCM no admite más audio." }
                                    delay(2)
                                } else { offset += written; progressed = System.nanoTime() }
                            }
                        }
                        totalFrames += preRollFrames
                    }
                    observing = launch {
                        try {
                            withTimeoutOrNull(5000) { while (current.head() <= preRollFrames) delay(5); firstFrame() }
                        } catch (cancel: CancellationException) { throw cancel }
                        catch (_: IllegalStateException) { /* salida cancelada antes de la primera trama */ }
                    }
                }
                withContext(Dispatchers.IO) {
                    var offset = 0
                    var progressed = System.nanoTime()
                    while (offset < audio.bytes.size) {
                        ensureActive()
                        val written = current.write(audio.bytes, offset, minOf(8192, audio.bytes.size - offset))
                        check(written >= 0)
                        if (written == 0) {
                            check(System.nanoTime() - progressed < 3_000_000_000L) { "La salida PCM no admite más audio." }
                            delay(2)
                        } else { offset += written; progressed = System.nanoTime() }
                    }
                }
                totalFrames += audio.bytes.size / 2L
            }
            output?.let { current ->
                val completed = withTimeoutOrNull(totalFrames * 1000 / rate + 3000) {
                    while (current.head() < totalFrames) delay(10)
                    true
                } == true
                check(completed) { "La salida no confirmó la reproducción PCM." }
            }
        } finally {
            observing?.cancel()
            val current = output
            synchronized(ownerLock) { if (track === current) track = null }
            current?.close()
        }
        Unit
    }
    fun stop() {
        val previous = synchronized(ownerLock) { revision++; track.also { track = null } }
        previous?.close()
    }
}
