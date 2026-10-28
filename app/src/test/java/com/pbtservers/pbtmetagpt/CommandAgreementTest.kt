package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class CommandAgreementTest {
    private val candidate = CommandHypothesis("abre ge pe te live", .96f, 1.0, 2.0)
    @Test fun siglasFoneticamenteReconocidasConservanLaOrdenCompleta() {
        assertTrue(ChatGptCommandGrammar.accepts(CommandHypothesis("abre eje peter", .6f, 1.03, 1.8), candidate, SpeechBounds(.99, 2.0)))
        ChatGptCommandGrammar.phrases.forEach {
            assertEquals(LocalVoiceCommand.CHATGPT, VoiceCommands.parse(it, "Jarvis", ListeningOptions()))
        }
    }
    @Test fun gramaticaNoPuedeOmitirNegacionNiExplicacionPrevia() {
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("no abre ese chat", 1f, .7, 2.0), candidate))
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("explicame como abre esto", 1f, .2, 2.0), candidate))
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("no quiero hablar con chat", 1f, 1.0, 2.0), candidate))
    }
    @Test fun continuacionDeFraseNoSeConfundeConOrdenAislada() {
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("abre ese chat mañana", 1f, 1.0, 2.8), candidate))
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("esto es para mañana", 1f, 1.0, 2.0), candidate.copy(text = "para")))
    }
    @Test fun confianzaInvalidaYDatosIncompletosNoAutorizanLanzamiento() {
        assertFalse(ChatGptCommandGrammar.accepts(candidate, candidate.copy(confidence = .8f)))
        assertFalse(ChatGptCommandGrammar.accepts(candidate, candidate.copy(confidence = Float.NaN)))
        assertFalse(ChatGptCommandGrammar.accepts(candidate.copy(text = ""), candidate))
        assertFalse(ChatGptCommandGrammar.accepts(candidate.copy(onsetSeconds = Double.NaN), candidate))
    }
    @Test fun ecoDeSiglasEsEquivalenteALaTranscripcionFonetica() {
        assertTrue(VoiceCommands.matchesEcho("abre ge pe te live", "Puedes decir Abre GPT Live."))
        assertTrue(VoiceCommands.matchesEcho("quiero hablar con chat ge pe te", "Di: Quiero hablar con ChatGPT."))
        assertTrue(VoiceCommands.matchesEcho("para", "Esto es para mañana."))
        assertFalse(VoiceCommands.matchesEcho("para", "Comparamos mañana."))
        assertFalse(VoiceCommands.matchesEcho("abre ge pe te live", "Mañana hará sol."))
    }
    @Test fun cierrePersonalizadoNoPuedeOcultarOrdenesDeParadaOChatGpt() {
        listOf("!!!", "Para", "Deja de escuchar", "Inicia GPT Live", "Abre ChatGPT en voz", "quiero hablar con chat ge pe te").forEach { phrase ->
            try { ListeningOptions(endPhrase = phrase).validated(); fail("Debe rechazar $phrase") }
            catch (_: IllegalArgumentException) { }
        }
        assertEquals("fin de sesión", ListeningOptions(endPhrase = "fin de sesión").validated().endPhrase)
    }
    @Test fun paraNecesitaDosDecodificadoresYUnaIntervencionAislada() {
        val open = CommandHypothesis("para", .59f, 1.0, 1.4)
        val restricted = open.copy(confidence = .99f)
        assertTrue(ChatGptCommandGrammar.accepts(open, restricted, SpeechBounds(.99, 1.42)))
        assertFalse(ChatGptCommandGrammar.accepts(open.copy(text = "para mañana"), restricted, SpeechBounds(.99, 1.42)))
        assertFalse(ChatGptCommandGrammar.accepts(open.copy(text = ""), restricted, SpeechBounds(.99, 1.42)))
        assertFalse(ChatGptCommandGrammar.accepts(open, restricted, SpeechBounds(.7, 1.7)))
    }
}
