package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class AdaptiveVadTest {
    private fun frame(level: Int) = ShortArray(320) { if (it % 2 == 0) level.toShort() else (-level).toShort() }
    @Test fun silencioYRuidoBajoNoAbrenLaCaptura() {
        val vad = AdaptiveVad()
        repeat(200) { assertEquals(AdaptiveVad.Event.WAIT, vad.accept(frame(25 + it % 25))) }
        assertFalse(vad.started); assertFalse(vad.enoughVoice())
    }
    @Test fun vozSuaveTrasRuidoSeDetectaSinUmbralRigidoDe450() {
        val vad = AdaptiveVad()
        repeat(30) { vad.accept(frame(15)) }
        repeat(10) { vad.accept(frame(110)) }
        assertTrue(vad.started); assertTrue(vad.enoughVoice())
    }
    @Test fun pausaBreveNoFinalizaYElSilencioSostenidoSi() {
        val vad = AdaptiveVad()
        repeat(15) { vad.accept(frame(1000)) }
        repeat(25) { assertNotEquals(AdaptiveVad.Event.END, vad.accept(frame(10))) }
        repeat(10) { vad.accept(frame(800)) }
        repeat(54) { assertNotEquals(AdaptiveVad.Event.END, vad.accept(frame(10))) }
        assertEquals(AdaptiveVad.Event.END, vad.accept(frame(10)))
    }
    @Test fun chasquidoDe20msNoCuentaComoPregunta() {
        val vad = AdaptiveVad()
        vad.accept(frame(9000)); repeat(100) { vad.accept(frame(0)) }
        assertFalse(vad.started); assertFalse(vad.enoughVoice())
    }
    @Test fun preRollConservaOrdenPrimeraSilabaYBorraDatos() {
        val ring = PcmPreRoll(6)
        ring.append(shortArrayOf(1, 2, 3, 4)); ring.append(shortArrayOf(5, 6, 7))
        assertArrayEquals(shortArrayOf(2, 3, 4, 5, 6, 7), ring.snapshot())
        ring.clear(); assertTrue(ring.snapshot().isEmpty())
    }
    @Test fun silenciosSeRecuperanSinLimiteArtificialDeOchoSesiones() {
        repeat(30) { assertEquals(180L, WakeRecovery.delayMillis(6, it)) }
        assertEquals(350L, WakeRecovery.delayMillis(8, 1))
        assertEquals(2800L, WakeRecovery.delayMillis(8, 4))
        assertNull(WakeRecovery.delayMillis(8, 5))
        assertNull(WakeRecovery.delayMillis(9, 1))
    }
    @Test fun dictadoUsaConfianzaValidaYConservaOrdenSinPuntuaciones() {
        assertEquals("hola", DictationHypotheses.best(listOf("", "hola"), null))
        assertEquals("segunda", DictationHypotheses.best(listOf("primera", "segunda"), floatArrayOf(.2f, .8f)))
        assertEquals("primera", DictationHypotheses.best(listOf("primera", "segunda"), floatArrayOf(Float.NaN, -1f)))
        assertEquals("primera", DictationHypotheses.best(listOf("primera", "segunda"), floatArrayOf(.8f)))
    }
    @Test fun respuestaSinPuntuacionSeFragmentaSinPerderNiCortarPalabras() {
        val original = (1..130).joinToString(" ") { "palabra$it" }
        val splitter = SpeechSegments(false)
        val phrases = original.chunked(13).flatMap { splitter.append(it) } + listOfNotNull(splitter.finish())
        assertTrue(phrases.size > 2)
        assertTrue(phrases.all { it.length <= 260 })
        assertEquals(original, phrases.joinToString(" "))
    }
}
