package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class WakeAlternativesTest {
    @Test fun hipotesisAlternativaActivaSoloEnSensibilidadPermitida() {
        val profile = AssistantProfile(name = "Luna")
        for (level in listOf("baja", "normal", "alta")) {
            val candidate = profile.copy(wakeSensitivity = level)
            val gate = ActivationGate(); val epoch = gate.arm(listOf(candidate))
            val match = gate.accept(listOf("un ruido", "ATÉNTO, Luna"), epoch)
            if (level == "baja") assertNull(match) else assertEquals(candidate, match)
        }
    }
    @Test fun alternativasAmbiguasYConversacionesAjenasNoAbrenSesion() {
        val a = AssistantProfile(name = "Luna"); val b = AssistantProfile(name = "Friday")
        val gate = ActivationGate(); val epoch = gate.arm(listOf(a, b))
        assertNull(gate.accept(listOf("Atento Luna", "Atento Friday"), epoch))
        assertNull(gate.acceptUtterance(listOf("él dijo atento Luna responde"), epoch))
        assertNotNull(gate.accept(listOf("Atento Luna"), epoch))
        assertNull(gate.accept(listOf("Atento Luna"), epoch))
    }
    @Test fun altaToleraUnaLetraPeroNoElPrefijoNiPalabrasMuyCortas() {
        val a = AssistantProfile(name = "Jarvis", wakeSensitivity = "alta")
        assertEquals(a, PhraseMatcher.resolve("Atento Yarvis", listOf(a)))
        assertNull(PhraseMatcher.resolve("Invento Jarvis", listOf(a)))
        assertNull(PhraseMatcher.resolve("Atento Luza", listOf(a.copy(name = "Luna"))))
    }
    @Test fun conservaPrimeraPreguntaSinVolverAAbrirElMicrofono() {
        val a = AssistantProfile(name = "Luna")
        val gate = ActivationGate(); val epoch = gate.arm(listOf(a))
        val match = gate.acceptUtterance(listOf("Atento Luna, ¿qué hora es?"), epoch)
        assertEquals(a, match?.profile)
        assertEquals("¿qué hora es?", match?.question)
        assertNull(gate.acceptUtterance(listOf("Atento Luna"), epoch))
    }
    @Test fun fraseDeUnaPalabraNoPermitePrefijoDeConversaciones() {
        val a = AssistantProfile(name = "Luna", activationPhrases = listOf(ActivationPhrase(text = "Atento", appendName = false)))
        val gate = ActivationGate(); val epoch = gate.arm(listOf(a))
        assertNull(gate.acceptUtterance(listOf("Atento a lo que dice"), epoch))
        assertNotNull(gate.acceptUtterance(listOf("Atento"), epoch))
    }
}
