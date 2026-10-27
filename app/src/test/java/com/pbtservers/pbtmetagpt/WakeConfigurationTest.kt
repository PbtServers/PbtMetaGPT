package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WakeConfigurationTest {
    @Test fun `migra perfiles anteriores sin imponer nuevas frases`() {
        val old = AssistantProfile(name = "Jarvis").json().apply { remove("activationPhrases") }
        val profile = AssistantProfile.parse(old)
        assertEquals("Atento Jarvis", profile.phrase)
        val text = JSONObject().put("version", 1).put("profiles", org.json.JSONArray().put(old)).toString()
        assertEquals(profile, AssistantProfile.import(text).single())
    }
    @Test fun `crear editar desactivar eliminar y cambiar nombre conserva configuracion`() {
        val first = ActivationPhrase(text = "Escucha", primary = true)
        val second = ActivationPhrase(text = "Jarvis", appendName = false)
        val profile = AssistantProfile(name = "Jarvis", activationPhrases = listOf(first, second), avatar = "🤖", confirmationSound = true, welcomeMessage = "Hola", sessionMinutes = 3)
        assertEquals(profile, AssistantProfile.import(AssistantProfile.export(listOf(profile))).single())
        assertEquals("Escucha Alex", profile.copy(name = "Alex").phrase)
        val changed = profile.copy(activationPhrases = listOf(first.copy(text = "Oye"), second.copy(enabled = false)))
        assertNotNull(PhraseMatcher.resolve("Oye Jarvis", listOf(changed)))
        assertNull(PhraseMatcher.resolve("Jarvis", listOf(changed)))
        assertEquals("Sin frases activas", profile.copy(activationPhrases = emptyList()).validated().phrase)
    }
    @Test fun `elige personaje correcto y rechaza ambiguedad`() {
        val a = AssistantProfile(name = "Jarvis")
        val b = AssistantProfile(name = "Friday", activationPhrases = listOf(ActivationPhrase(text = "Escucha")))
        assertEquals(b, PhraseMatcher.resolve("ESCUCHA Friday", listOf(a, b)))
        assertNull(PhraseMatcher.resolve("Atento Jarvis", listOf(a, a.copy(id = "otro"))))
        assertNull(PhraseMatcher.resolve("no digas atento Jarvis", listOf(a)))
        assertNull(PhraseMatcher.resolve("Atento Jarvis abre la cámara", listOf(a)))
    }
    @Test fun `resultado duplicado o antiguo no dispara otra sesion`() {
        val gate = ActivationGate(); val a = AssistantProfile(name = "Jarvis")
        val first = gate.arm(listOf(a))
        assertEquals(a, gate.accept("Atento Jarvis", first))
        assertNull(gate.accept("Atento Jarvis", first))
        val second = gate.arm(listOf(a)); assertNull(gate.accept("Atento Jarvis", first))
        gate.stop(); assertNull(gate.accept("Atento Jarvis", second)); assertFalse(gate.armed)
    }
    @Test fun `tolerancia opcional y ninguna cuota artificial de frases`() {
        val phrases = (0 until 500).map { ActivationPhrase(text = "Escucha número $it", appendName = false) }
        val a = AssistantProfile(name = "Jarvis", activationPhrases = phrases)
        assertEquals(500, AssistantProfile.import(AssistantProfile.export(listOf(a))).single().activationPhrases.size)
        assertEquals(a, PhraseMatcher.resolve("Escucha número 499", listOf(a)))
        assertNull(PhraseMatcher.resolve("Atento Yarvis", listOf(AssistantProfile(name = "Jarvis"))))
        assertNotNull(PhraseMatcher.resolve("Atento Yarvis", listOf(AssistantProfile(name = "Jarvis", tolerantActivation = true))))
    }
    @Test fun `exportacion no admite campos secretos en configuraciones nuevas`() {
        assertTrue(runCatching { ActivationPhrase.parse(ActivationPhrase().json().put("api_key", "no")) }.isFailure)
        assertTrue(runCatching { AssistantProfile(name = "Jarvis", welcomeMessage = "sk-" + "a".repeat(40)).validated() }.isFailure)
        assertTrue(runCatching { AssistantProfile(name = "Jarvis", activationPhrases = listOf(ActivationPhrase(), ActivationPhrase(primary = true), ActivationPhrase(primary = true))).validated() }.isFailure)
    }
}
