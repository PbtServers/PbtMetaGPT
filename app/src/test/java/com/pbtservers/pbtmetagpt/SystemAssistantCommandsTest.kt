package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class SystemAssistantCommandsTest {
    @Test fun alternativaLibreCompletaCorroboraElAsistenteSinInventarConfianza() {
        val free = CommandHypothesis("abre el asistente su c c c f", .548778f, 1.0, 3.0)
        val restricted = free.copy(text = "abre el asistente del teléfono", confidence = .99f)
        val bounds = SpeechBounds(1.0, 3.0)
        assertFalse(SystemAssistantCommandGrammar.accepts(free, restricted, bounds))
        assertTrue(SystemAssistantCommandGrammar.acceptsAlternatives(free, listOf(free.text, restricted.text), restricted, bounds))
        assertFalse(SystemAssistantCommandGrammar.acceptsAlternatives(free, listOf("abre el asistente del hotel"), restricted, bounds))
        assertFalse(SystemAssistantCommandGrammar.acceptsAlternatives(free.copy(confidence = 1f), listOf(restricted.text), restricted, bounds))
        assertFalse(SystemAssistantCommandGrammar.acceptsAlternatives(free, listOf(restricted.text), restricted.copy(confidence = .94f), bounds))
        assertFalse(SystemAssistantCommandGrammar.acceptsAlternatives(free, listOf(restricted.text), restricted, SpeechBounds(.5, 3.0)))
    }
    @Test fun acuerdoTextoCompletoYConfianzaAltaResuelvenLaOrdenSinRebajarElUmbral() {
        val free = CommandHypothesis("abre el asistente del teléfono", .718073f, 1.0, 2.5)
        val restricted = free.copy(confidence = .99f)
        val bounds = SpeechBounds(1.0, 2.5)
        assertTrue(SystemAssistantCommandGrammar.accepts(free, restricted, bounds))
        assertFalse(SystemAssistantCommandGrammar.accepts(free, restricted.copy(confidence = .94f), bounds))
        assertFalse(SystemAssistantCommandGrammar.accepts(free, restricted.copy(confidence = Float.NaN), bounds))
        assertFalse(SystemAssistantCommandGrammar.accepts(free, restricted, SpeechBounds(.7, 2.5)))
        assertFalse(SystemAssistantCommandGrammar.accepts(free, restricted, SpeechBounds(1.0, 2.9)))
    }
    @Test fun gramaticaNoPuedeConvertirPalabrasParecidasONegacionesEnApertura() {
        val google = CommandHypothesis("abre google", 1f, 1.0, 2.0)
        val bounds = SpeechBounds(1.0, 2.0)
        listOf("regula el", "agregó", "no abre google", "explica cómo abre google", "abre google mañana", "abre el asistente del teléfono").forEach {
            assertFalse(it, SystemAssistantCommandGrammar.accepts(google.copy(text = it), google, bounds))
        }
    }
    @Test fun googleYAsistenteSonDestinosDistintos() {
        assertEquals(LocalVoiceCommand.GOOGLE, VoiceCommands.parse("Abre Google", "Jarvis", ListeningOptions()))
        assertEquals(LocalVoiceCommand.GOOGLE, VoiceCommands.parse("Google", "Jarvis", ListeningOptions()))
        listOf("Jarvis, abre el asistente de Google", "Abre el asistente del teléfono", "Quiero hablar con Google").forEach {
            assertEquals(LocalVoiceCommand.SYSTEM_ASSISTANT, VoiceCommands.parse(it, "Jarvis", ListeningOptions()))
        }
    }
    @Test fun negacionesMencionesYEcoNoAbrenAsistentes() {
        listOf("No abre Google", "No quiero hablar con Google", "Explícame cómo abre Google", "Busca algo en Google").forEach {
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
