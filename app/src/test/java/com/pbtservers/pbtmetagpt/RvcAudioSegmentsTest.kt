package com.pbtservers.pbtmetagpt

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayOutputStream

class RvcAudioSegmentsTest {
    @Test fun conservaMuestrasYNoCreaColasDemasiadoCortasParaRvc() {
        for (rate in listOf(8000, 16000, 24000, 48000)) for (extra in listOf(0, 2, 3000, 4000, rate * 2 * 10)) {
            val original = ByteArray(rate * 2 * 7 + extra) { (it % 128).toByte() }
            val segments = RvcAudioSegments.split(PcmAudio(original, rate))
            assertTrue(segments.all { it.bytes.size in 3200..rate * 2 * 8 && it.bytes.size % 2 == 0 && it.sampleRate == rate })
            val joined = ByteArrayOutputStream().also { out -> segments.forEach { out.write(it.bytes) } }.toByteArray()
            assertArrayEquals(original, joined)
        }
    }
}
