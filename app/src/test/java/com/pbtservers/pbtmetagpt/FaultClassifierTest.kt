package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class FaultClassifierTest {
    @Test fun `clasifica autenticacion API backend y Keystore sin exportar cuerpos`() {
        listOf("OPENAI_HTTP_401", "SDP_HTTP_403", "OPENAI_AUTH", "BACKEND_AUTH", "KEYSTORE_ERROR", "KEY_MISSING").forEach {
            assertEquals(Fault.AUTHENTICATION, FaultClassifier.connection(it))
        }
    }
    @Test fun `conserva la distincion entre cuota y credencial`() {
        listOf("OPENAI_HTTP_429", "OPENAI_QUOTA", "BACKEND_RATE_LIMIT").forEach { assertEquals(Fault.QUOTA, FaultClassifier.connection(it)) }
    }
    @Test fun `distingue red de certificado`() {
        listOf("NETWORK_TIMEOUT", "DNS_ERROR", "OPENAI_NETWORK", "OPENAI_TIMEOUT").forEach { assertEquals(Fault.NETWORK, FaultClassifier.connection(it)) }
        assertEquals(Fault.TLS, FaultClassifier.connection("TLS_ERROR"))
    }
    @Test fun `detecta negociacion fallida o credencial temporal invalida`() {
        listOf("SDP_HTTP_500", "OPENAI_INVALID_SECRET").forEach { assertEquals(Fault.SESSION_REJECTED, FaultClassifier.connection(it)) }
    }
    @Test fun `una entrada desconocida solo devuelve un valor cerrado`() {
        val externalCode = "https://privado.invalid/?token=contenido-personal"
        val result = FaultClassifier.connection(externalCode)
        assertEquals(Fault.INITIALIZATION, result); assertFalse(result.name.contains(externalCode))
    }
}
