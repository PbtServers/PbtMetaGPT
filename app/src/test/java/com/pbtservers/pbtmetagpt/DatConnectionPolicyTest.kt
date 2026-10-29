package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class DatConnectionPolicyTest {
    @Test fun pausarConversacionRevocaElRearmeDelServicio() {
        val listening = WakeSessionPolicy().apply { authorize() }
        listening.onVoice(true, false, false)
        assertFalse(listening.onVoice(true, false, false, paused = true))
        assertFalse(listening.authorized)
        assertEquals(ListeningPhase.SUSPENDED, listening.phase)
        assertFalse(listening.onVoice(false, false, false))
    }
    @Test fun inicioBloqueoYRecreacionConservanSoloPropietariosAutorizados() {
        assertTrue(DatConnectionPolicy.retain(false, true, false))
        assertTrue(DatConnectionPolicy.retain(false, false, true))
        assertTrue(DatConnectionPolicy.retain(true, false, false))
        assertFalse(DatConnectionPolicy.retain(false, false, false))
    }
    @Test fun recuperacionEsFinitaYLaParadaExplicitaRevoca() {
        val policy = DatConnectionPolicy()
        assertNull(policy.nextDelay(true, true, false))
        policy.authorize()
        assertEquals(2000L, policy.nextDelay(true, true, false))
        assertEquals(5000L, policy.nextDelay(true, true, false))
        assertEquals(15000L, policy.nextDelay(true, true, false))
        repeat(20) { assertNull(policy.nextDelay(true, true, false)) }
        policy.authorize(); policy.revoke()
        assertNull(policy.nextDelay(true, true, false))
    }
    @Test fun noReintentaSinEnlaceSinPropietarioNiDuranteConflictoHfp() {
        val policy = DatConnectionPolicy().apply { authorize() }
        assertNull(policy.nextDelay(false, true, false))
        assertNull(policy.nextDelay(true, false, false))
        assertNull(policy.nextDelay(true, true, true))
        assertEquals(0, policy.attempts)
        assertEquals(2000L, policy.nextDelay(true, true, false))
    }
}
