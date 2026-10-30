package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class WavAudioTest {
    private fun little32(buffer: ByteArray, offset: Int) =
        (buffer[offset].toInt() and 255) or ((buffer[offset + 1].toInt() and 255) shl 8) or
            ((buffer[offset + 2].toInt() and 255) shl 16) or ((buffer[offset + 3].toInt() and 255) shl 24)

    @Test fun construyeWavValidoSinAlterarLaMuestra() {
        val pcm = byteArrayOf(0, 0, 1, 0, -1, 127)
        val wav = WavAudio.encodePcm16Mono(16000, pcm)
        assertEquals("RIFF", wav.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", wav.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals(36 + pcm.size, little32(wav, 4))
        assertEquals(16000, little32(wav, 24))
        assertEquals(pcm.size, little32(wav, 40))
        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }
    @Test fun rechazaAudioVacioODemasiadoGrande() {
        assertTrue(runCatching { WavAudio.encodePcm16Mono(16000, byteArrayOf()) }.isFailure)
        assertTrue(runCatching { WavAudio.encodePcm16Mono(16000, ByteArray(960002)) }.isFailure)
        assertTrue(runCatching { WavAudio.encodePcm16Mono(16000, ByteArray(31)) }.isFailure)
        assertTrue(runCatching { WavAudio.encodePcm16Mono(8000, ByteArray(32)) }.isFailure)
    }
    @Test fun admitePreguntasDe28SegundosSinConservarElAntiguoCorteDe12Segundos() {
        val pcm = ByteArray(16000 * 2 * 28)
        val wav = WavAudio.encodePcm16Mono(16000, pcm)
        assertEquals(pcm.size + 44, wav.size)
        assertEquals(pcm.size, little32(wav, 40))
        assertEquals(960044, WavAudio.encodePcm16Mono(16000, ByteArray(960000)).size)
    }
}
