package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

class MicrophoneGateTest {
    @Test fun `los motores locales y Realtime nunca adquieren el microfono simultaneamente`() {
        MicrophoneOwner.entries.forEach { first -> MicrophoneOwner.entries.forEach { second ->
            val gate = MicrophoneGate(); val local = gate.acquire(first)
            assertNotNull(local); assertNull(gate.acquire(second))
            gate.release(local); val next = gate.acquire(second)
            assertNotNull(next); assertEquals(second, gate.owner())
        } }
    }
    @Test fun `liberacion duplicada antigua o nula no libera una conversacion nueva`() {
        val gate = MicrophoneGate(); val first = gate.acquire(MicrophoneOwner.CONTINUOUS_LOCAL)
        gate.release(first); val second = gate.acquire(MicrophoneOwner.REALTIME)
        gate.release(first); gate.release(null)
        assertEquals(MicrophoneOwner.REALTIME, gate.owner()); assertNull(gate.acquire(MicrophoneOwner.ANDROID_LOCAL))
        gate.release(second); gate.release(second); assertNull(gate.owner())
    }
    @Test fun `dos hilos que solicitan captura a la vez solo obtienen una concesion`() {
        val gate = MicrophoneGate(); val start = CountDownLatch(1); val pool = Executors.newFixedThreadPool(2)
        try {
            val results = MicrophoneOwner.entries.take(2).map { owner -> pool.submit<MicrophoneGate.Lease?> { start.await(); gate.acquire(owner) } }
            start.countDown()
            assertEquals(1, results.map { it.get() }.count { it != null })
        } finally { pool.shutdownNow() }
    }
}
