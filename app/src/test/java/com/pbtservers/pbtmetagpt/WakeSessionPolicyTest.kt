package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class WakeSessionPolicyTest {
    @Test fun `termina vuelve a espera y admite tres conversaciones sin otra autorizacion`() {
        val policy = WakeSessionPolicy().apply { authorize() }
        repeat(3) {
            val token = requireNotNull(policy.beginListening())
            assertTrue(policy.ready(token)); assertTrue(policy.detected(token))
            policy.preparing(); policy.onVoice(true, false, false)
            assertNull(policy.beginListening()) // Una conversación viva no puede ceder el micrófono.
            assertTrue(policy.onVoice(false, false, false))
            assertTrue(policy.authorized); assertEquals(ListeningPhase.RETURNING, policy.phase)
            assertFalse(policy.detected(token))
        }
        val next = requireNotNull(policy.beginListening())
        assertTrue(policy.ready(next)); assertEquals(ListeningPhase.LISTENING, policy.phase)
        policy.stop() // «Deja de escuchar» revoca también el siguiente inicio.
        assertNull(policy.beginListening())
    }
    @Test fun `una preparacion nueva durante la liberacion impide un rearme antiguo`() {
        val policy = WakeSessionPolicy().apply { authorize(); onVoice(true, false, false) }
        assertTrue(policy.onVoice(false, false, false))
        policy.preparing()
        assertNull(policy.beginListening())
    }
    @Test fun `sin accion explicita no escucha ni vuelve de una sesion`() {
        val policy = WakeSessionPolicy()
        assertNull(policy.beginListening()); assertFalse(policy.onVoice(true, false, false))
        assertEquals(ListeningPhase.OFF, policy.phase)
    }
    @Test fun `activacion conecta y vuelve a escuchar despues de finalizar una conversacion confirmada`() {
        val policy = WakeSessionPolicy(); policy.authorize()
        val token = requireNotNull(policy.beginListening()); assertTrue(policy.ready(token)); assertTrue(policy.detected(token))
        policy.preparing(); assertFalse(policy.onVoice(false, true, false))
        assertEquals(ListeningPhase.PREPARING, policy.phase)
        assertFalse(policy.onVoice(true, false, false)); assertEquals(ListeningPhase.CONVERSATION, policy.phase)
        assertTrue(policy.onVoice(false, false, false)); assertEquals(ListeningPhase.RETURNING, policy.phase)
        assertNotNull(policy.beginListening()); assertTrue(policy.authorized)
    }
    @Test fun `no duplica la sesion ante callbacks repetidos o antiguos`() {
        val policy = WakeSessionPolicy(); policy.authorize(); val old = requireNotNull(policy.beginListening())
        assertTrue(policy.ready(old)); assertTrue(policy.detected(old)); assertFalse(policy.detected(old))
        policy.preparing(); assertFalse(policy.detected(old)); assertFalse(policy.ready(old))
    }
    @Test fun `un error de autenticacion o conexion suspende sin bucle de reintentos`() {
        val policy = WakeSessionPolicy(); policy.authorize(); policy.preparing()
        assertFalse(policy.onVoice(false, false, true)); assertFalse(policy.authorized)
        assertEquals(ListeningPhase.SUSPENDED, policy.phase); assertNull(policy.beginListening())
    }
    @Test fun `perder permisos o audio durante conversacion impide el rearme automatico`() {
        val policy = WakeSessionPolicy(); policy.authorize(); policy.preparing(); policy.onVoice(true, false, false)
        assertFalse(policy.onVoice(false, false, true)); assertNull(policy.beginListening())
        assertEquals(ListeningPhase.SUSPENDED, policy.phase)
    }
    @Test fun `desactivar invalida todo hasta una nueva autorizacion explicita`() {
        val policy = WakeSessionPolicy(); policy.authorize(); val token = requireNotNull(policy.beginListening())
        policy.stop(); assertFalse(policy.ready(token)); assertFalse(policy.detected(token))
        assertFalse(policy.onVoice(false, false, false)); assertNull(policy.beginListening())
        policy.authorize(); val next = requireNotNull(policy.beginListening()); assertNotEquals(token, next); assertTrue(policy.ready(next))
    }
}
