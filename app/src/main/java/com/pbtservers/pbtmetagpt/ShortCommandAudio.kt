package com.pbtservers.pbtmetagpt

/** Hasta seis segundos en RAM para comprobar una orden; nunca disco ni red. */
class ShortCommandAudio : AutoCloseable {
    private val bytes = ByteArray(192000)
    private var size = 0
    private var exceeded = false
    fun append(samples: ShortArray, count: Int) {
        if (exceeded) return
        if (size + count * 2 > bytes.size) { exceeded = true; bytes.fill(0); return }
        repeat(count) { index -> bytes[size++] = samples[index].toByte(); bytes[size++] = (samples[index].toInt() shr 8).toByte() }
    }
    fun wav(): ByteArray? = if (exceeded || size == 0) null else {
        val pcm = bytes.copyOf(size)
        try { WavAudio.encodePcm16Mono(16000, pcm) } finally { pcm.fill(0) }
    }
    override fun close() { bytes.fill(0); size = 0; exceeded = true }
}
