package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

/** Contratos locales: no constituyen pruebas del servicio remoto. */
class QwenSpaceContractTest {
    @Test fun colaEsperaCompletoYNoConfundeHeartbeatConResultado() {
        val stream = "event: heartbeat\ndata: null\n\nevent: complete\ndata: [{\"url\":\"archivo\"},\"ok\"]\n\n"
        assertEquals("archivo", QwenSpaceContract.result(stream).getJSONObject(0).getString("url"))
        assertTrue(runCatching { QwenSpaceContract.result("event: heartbeat\ndata: null\n\n") }.isFailure)
    }
    @Test fun cuotaSseSeDistingueSinExponerMensajeRemoto() {
        val error = runCatching { QwenSpaceContract.result("event: error\ndata: \"GPU quota exceeded contenido_privado\"\n\n") }.exceptionOrNull() as HostedFailure
        assertTrue(error.publicMessage.contains("Cuota gratuita"))
        assertFalse(error.publicMessage.contains("contenido_privado"))
    }
    @Test fun audioSoloDelOrigenYRutaAutorizados() {
        val valid = QwenSpaceContract.origin + "/gradio_api/file=/tmp/gradio/audio.wav"
        assertEquals(valid, QwenSpaceContract.fileUrl(valid))
        for (url in listOf("http://qwen-qwen3-tts.hf.space/gradio_api/file=a", "https://otro.hf.space/gradio_api/file=a",
            "https://qwen-qwen3-tts.hf.space:8443/gradio_api/file=a", "https://usuario:clave@qwen-qwen3-tts.hf.space/gradio_api/file=a",
            "https://qwen-qwen3-tts.hf.space/gradio_api/file=a?token=valor", "https://qwen-qwen3-tts.hf.space/api/otro")) {
            assertTrue(runCatching { QwenSpaceContract.fileUrl(url) }.isFailure)
        }
    }
    @Test fun idiomaCatalanSeRechazaExplicitamente() {
        assertEquals("Spanish", QwenSpaceContract.language("es-ES"))
        assertTrue(runCatching { QwenSpaceContract.language("ca-ES") }.exceptionOrNull() is HostedFailure)
    }
    @Test fun perfilQwenSeExportaSinCredencialesYConVozYModelo() {
        val profile = AssistantProfile(name = "Luna", ttsEngine = "qwen_space", ttsVoice = "Ryan", ttsPackage = "0.6B")
        assertEquals(profile, AssistantProfile.import(AssistantProfile.export(listOf(profile))).single())
        val data = QwenSpaceContract.data(profile, "Hola").getJSONArray("data")
        assertEquals(5, data.length()); assertEquals("Spanish", data.getString(1)); assertEquals("Ryan", data.getString(2))
    }
    @Test fun perfilNoAdmiteModelosDeDisenoNiVocesInexistentes() {
        for (profile in listOf(AssistantProfile(name = "Luna", ttsEngine = "qwen_space", ttsVoice = "Ryan", ttsPackage = "VoiceDesign"),
            AssistantProfile(name = "Luna", ttsEngine = "qwen_space", ttsVoice = "Inventada", ttsPackage = "0.6B"))) {
            assertTrue(runCatching { profile.validated() }.isFailure)
        }
    }
}
