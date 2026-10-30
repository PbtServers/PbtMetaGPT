package com.pbtservers.pbtmetagpt

import java.io.ByteArrayOutputStream

/** WAV PCM mono, sin metadatos ni identidad; hasta 30 s, incluido prebuffer. */
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
