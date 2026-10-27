package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class HostedPolicyTest {
    @Test fun cuotaDetieneTurnoYRespetaRetryAfterAcotado() {
        assertTrue(HostedPolicy.failure(429, "12").publicMessage.contains("12 segundos"))
        assertTrue(HostedPolicy.failure(429, "999999999").publicMessage.contains("86400 segundos"))
        assertFalse(HostedPolicy.failure(429, "contenido privado").publicMessage.contains("contenido privado"))
    }
    @Test fun diferenciaCredencialModeloYServicioSinCuerpoRemoto() {
        assertTrue(HostedPolicy.failure(401).publicMessage.contains("Credencial"))
        assertTrue(HostedPolicy.failure(404).publicMessage.contains("Modelo"))
        assertTrue(HostedPolicy.failure(503).publicMessage.contains("ocupado"))
    }
    @Test fun modoGratuitoRechazaTodosLosMotoresFacturablesYExternos() {
        for (engine in listOf("realtime", "openai_tts", "external", "sherpa_vits", "rvc", "desconocido")) assertFalse(HostedPolicy.freeVoice(engine))
        assertTrue(HostedPolicy.freeVoice("android")); assertTrue(HostedPolicy.freeVoice("qwen_space"))
    }
    @Test fun modeloAdmiteIdentificadorDeOrganizacion() {
        assertEquals("openai/gpt-oss-20b", HostedPolicy.model("openai/gpt-oss-20b"))
    }
    @Test(expected = IllegalArgumentException::class) fun modeloNoAdmiteURLNiParametros() { HostedPolicy.model("https://proveedor.invalid/?key=valor") }
    @Test(expected = IllegalArgumentException::class) fun modeloNoAdmiteCredenciales() { HostedPolicy.model("gsk_" + "x".repeat(40)) }
    @Test fun perfilesYTextosDetectanCredencialesDeLosNuevosProveedores() {
        assertTrue(SecretPatterns.contains("gsk_" + "x".repeat(40)))
        assertTrue(SecretPatterns.contains("hf_" + "x".repeat(40)))
        assertFalse(SecretPatterns.contains("Qwen3-TTS y GroqCloud"))
    }
}
