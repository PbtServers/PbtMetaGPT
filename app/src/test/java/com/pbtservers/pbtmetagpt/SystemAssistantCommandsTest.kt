package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class SystemAssistantCommandsTest {
    @Test fun abreGoogleEsUnaOrdenDeAsistenteYNoUnaBusqueda() {
        listOf("Abre Google", "Jarvis, abre el asistente de Google", "Abre el asistente del teléfono", "Quiero hablar con Google").forEach {
            assertEquals(LocalVoiceCommand.SYSTEM_ASSISTANT, VoiceCommands.parse(it, "Jarvis", ListeningOptions()))
        }
    }
    @Test fun negacionesMencionesYEcoNoAbrenAsistentes() {
        listOf("No abre Google", "No quiero hablar con Google", "Explícame cómo abre Google", "Busca algo en Google", "Google").forEach {
            assertNull(it, VoiceCommands.parse(it, "Jarvis", ListeningOptions()))
        }
        assertTrue(VoiceCommands.matchesEcho("abre google", "Para usarlo, di Abre Google."))
        assertNull(InterruptionGate().accept("abre google", 1f, 5000, 0, false, "Jarvis", ListeningOptions()))
        assertNull(InterruptionGate().accept("abre google", .3f, 5000, 0, true, "Jarvis", ListeningOptions()))
    }
    @Test fun fraseDeCierreNoPuedeOcultarLaTransferenciaDeMicrofono() {
        assertTrue(runCatching { ListeningOptions(endPhrase = "abre google").validated() }.isFailure)
        assertEquals(LocalVoiceCommand.CHATGPT, VoiceCommands.parse("abre ChatGPT", "Jarvis", ListeningOptions()))
    }
}
