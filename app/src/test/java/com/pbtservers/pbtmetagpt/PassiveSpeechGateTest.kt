package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class PassiveSpeechGateTest {
    @Test fun diezMinutosDeSilencioNoActivanInferencia() {
        val gate = PassiveSpeechGate()
        val silent = ShortArray(320)
        repeat(30_000) { assertEquals(PassiveSpeechGate.Event.WAIT, gate.accept(silent, silent.size)) }
        gate.clear()
    }
    @Test fun conservaInicioYRearmaSoloDespuesDeFinDeIntervencion() {
        val gate = PassiveSpeechGate()
        val silence = ShortArray(320)
        val voice = ShortArray(320) { if (it % 2 == 0) 3000 else -3000 }
        repeat(50) { gate.accept(silence, silence.size) }
        repeat(2) { assertEquals(PassiveSpeechGate.Event.WAIT, gate.accept(voice, voice.size)) }
        assertEquals(PassiveSpeechGate.Event.START, gate.accept(voice, voice.size))
        val prefix = gate.takePrefix()
        assertEquals(4800, prefix.size)
        assertTrue(prefix.takeLast(960).all { it != 0.toShort() })
        assertEquals(12160L, gate.startFrame)
        prefix.fill(0)
        assertEquals(PassiveSpeechGate.Event.FEED, gate.accept(voice, voice.size))
        repeat(64) { assertEquals(PassiveSpeechGate.Event.FEED, gate.accept(silence, silence.size)) }
        assertEquals(PassiveSpeechGate.Event.END, gate.accept(silence, silence.size))
        repeat(100) { assertEquals(PassiveSpeechGate.Event.WAIT, gate.accept(silence, silence.size)) }
    }
}
