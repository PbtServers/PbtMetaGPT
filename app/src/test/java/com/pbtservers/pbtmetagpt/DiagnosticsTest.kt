package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class DiagnosticsTest {
    @Test fun `las funciones físicas empiezan sin comprobar`() {
        val diagnostics = Diagnostics()
        assertTrue(diagnostics.state.value.entries.values.all { it.status == CheckStatus.PENDING })
        assertTrue(diagnostics.state.value.history.isEmpty())
    }
    @Test fun `la confirmacion manual queda separada de los eventos del sistema`() {
        val diagnostics = Diagnostics { 1234 }
        diagnostics.record(Check.LOCAL_SPEAKER, CheckStatus.OK, "El usuario ha oído el tono.", "USER_CONFIRMATION", manual = true)
        val result = diagnostics.state.value.entries.getValue(Check.LOCAL_SPEAKER)
        assertTrue(result.manual)
        assertEquals(1234L, result.time)
        assertTrue(diagnostics.report().contains("confirmación manual"))
        assertEquals(CheckStatus.PENDING, diagnostics.state.value.entries.getValue(Check.PLAYBACK).status)
    }
    @Test fun `el historial esta acotado y se puede borrar`() {
        val diagnostics = Diagnostics()
        repeat(100) { diagnostics.record(Check.ICE, CheckStatus.RUNNING, "Cambio de transporte.", "CHECKING") }
        assertEquals(60, diagnostics.state.value.history.size)
        diagnostics.reset()
        assertTrue(diagnostics.state.value.history.isEmpty())
        assertTrue(diagnostics.state.value.entries.values.all { it.status == CheckStatus.PENDING })
    }
    @Test fun `no se exportan credenciales ni cuerpos externos como codigos`() {
        for (code in listOf("sk-privada", "sk_privada", "ek_temporal", "Bearer token", "{\"error\":\"dato privado\"}")) assertEquals("", Diagnostics.safeCode(code))
        val result = RealtimeProtocol.backendFailure("""{"code":"OPENAI_AUTH","error":"sk-no-mostrar"}""", 502)
        assertEquals("OPENAI_AUTH", result.code)
        assertFalse(result.message.orEmpty().contains("sk-no-mostrar"))
        assertEquals("BACKEND_HTTP_502", RealtimeProtocol.backendFailure("""{"code":"token-personal-desconocido"}""", 502).code)
    }
}
