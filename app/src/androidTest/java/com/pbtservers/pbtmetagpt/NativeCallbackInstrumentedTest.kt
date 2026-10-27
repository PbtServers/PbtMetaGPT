package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NativeCallbackInstrumentedTest {
    @Test fun observerNuncaLiberaRecursosDentroDeSuPropiaPila() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var insideObserver = false
        var called = false
        val queue = NativeCallbackQueue(scope) { 1 }
        instrumentation.runOnMainSync {
            insideObserver = true
            queue.dispatch(1) { assertFalse(insideObserver); called = true }
            assertFalse(called)
            insideObserver = false
        }
        instrumentation.waitForIdleSync()
        assertTrue(called)
        scope.cancel()
    }
    @Test fun cerrarSesionInvalidaCallbacksYaEncolados() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        var generation = 1
        var called = false
        val queue = NativeCallbackQueue(scope) { generation }
        instrumentation.runOnMainSync { queue.dispatch(1) { called = true }; generation++ }
        instrumentation.waitForIdleSync()
        assertFalse(called)
        scope.cancel()
    }
}
