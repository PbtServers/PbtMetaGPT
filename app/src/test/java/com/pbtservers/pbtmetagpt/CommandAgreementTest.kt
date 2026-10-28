package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class CommandAgreementTest {
    private val candidate = CommandHypothesis("abre ge pe te live", .96f, 1.0, 2.0)
    @Test fun siglasFoneticamenteReconocidasConservanLaOrdenCompleta() {
        assertTrue(ChatGptCommandGrammar.accepts(CommandHypothesis("abre eje peter", .6f, 1.03, 1.8), candidate))
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
        assertFalse(ChatGptCommandGrammar.accepts(CommandHypothesis("para", 1f, 1.0, 2.0), candidate.copy(text = "para")))
    }
    @Test fun confianzaInvalidaYDatosIncompletosNoAutorizanLanzamiento() {
        assertFalse(ChatGptCommandGrammar.accepts(candidate, candidate.copy(confidence = .8f)))
        assertFalse(ChatGptCommandGrammar.accepts(candidate, candidate.copy(confidence = Float.NaN)))
        assertFalse(ChatGptCommandGrammar.accepts(candidate.copy(text = ""), candidate))
        assertFalse(ChatGptCommandGrammar.accepts(candidate.copy(onsetSeconds = Double.NaN), candidate))
    }
}
