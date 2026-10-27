package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class WebRtcBootstrapTest {
    @Test fun inicializacionUnaSolaVezTrasReconexiones() {
        var calls = 0
        val initializer = OnceInitializer { calls++ }
        repeat(100) { initializer.ensure() }
        assertEquals(1, calls)
    }

    @Test fun errorDeInicializacionNoBloqueaReintento() {
        var calls = 0
        val initializer = OnceInitializer {
            calls++
            if (calls == 1) throw IllegalStateException("simulacion")
        }
        assertThrows(IllegalStateException::class.java) { initializer.ensure() }
        initializer.ensure()
        initializer.ensure()
        assertEquals(2, calls)
    }

    @Test fun llamadasConcurrentesNoDuplicanInicializacion() {
        var calls = 0
        val initializer = OnceInitializer { calls++ }
        val threads = (0 until 8).map { Thread { repeat(50) { initializer.ensure() } } }
        threads.forEach(Thread::start)
        threads.forEach(Thread::join)
        assertEquals(1, calls)
    }
}
