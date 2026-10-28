package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class WakeConfirmationTest {
    @Test fun confirmacionGeneraAudioConAtaqueSuaveYNoSilencioso() {
        val cue = WakeConfirmation.pcm()
        assertEquals(16000, cue.sampleRate)
        assertEquals(16000 * 190 / 1000 * 2, cue.bytes.size)
        assertEquals(0, cue.bytes[0].toInt())
        assertEquals(0, cue.bytes[1].toInt())
        assertTrue(cue.bytes.any { it.toInt() != 0 })
    }
}
