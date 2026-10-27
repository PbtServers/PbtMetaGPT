package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.KeyGenerator

class AssistantProfileTest {
    @Test fun `ningun nombre se impone y crear requiere elegirlo`() {
        assertEquals("", AssistantProfile().name)
        assertTrue(runCatching { AssistantProfile().validated() }.isFailure)
        assertEquals("Atento Jarvis", AssistantProfile(name = " Jarvis ").validated().phrase)
    }
    @Test fun `persistencia completa e instrucciones del perfil seleccionado`() {
        val original = AssistantProfile(name = "Jarvis", userName = "Ana", description = "Ayuda", personality = "Analítico", tone = "Elegante", formal = true,
            humor = "Sutil", vocabulary = "Técnico", instructions = "Comprueba supuestos", voice = "cedar", language = "es-ES", detailed = true, speed = 0.9)
        assertEquals(original, AssistantProfile.import(AssistantProfile.export(listOf(original))).single())
        assertTrue(original.prompt().contains("Ana"))
        assertTrue(original.prompt().contains("Comprueba supuestos"))
        assertEquals("Atento Alex", original.copy(name = "Alex").phrase)
    }
    @Test fun `importacion rechaza secretos campos desconocidos tamano y versiones`() {
        val valid = AssistantProfile.export(listOf(AssistantProfile(name = "Jarvis")))
        for (bad in listOf(JSONObject(valid).put("api_key", "privada").toString(), JSONObject(valid).put("version", 99).toString(), "x".repeat(262145), "{}")) {
            assertTrue(runCatching { AssistantProfile.import(bad) }.isFailure)
        }
        val injected = JSONObject(valid)
        injected.getJSONArray("profiles").getJSONObject(0).put("token", "no permitido")
        assertTrue(runCatching { AssistantProfile.import(injected.toString()) }.isFailure)
        assertTrue(runCatching { AssistantProfile(name = "Jarvis", instructions = "sk-" + "x".repeat(30)).validated() }.isFailure)
        assertFalse(valid.contains("api_key"))
    }
    @Test fun `modelos voces y velocidad solo aceptan contrato compatible`() {
        RealtimeSettings.voices.forEach { voice ->
            val session = RealtimeSettings.session(AssistantProfile(name = "Alex", voice = voice))
            assertEquals("gpt-realtime", session.getString("model"))
            assertEquals(voice, session.getJSONObject("audio").getJSONObject("output").getString("voice"))
        }
        assertTrue(runCatching { AssistantProfile(name = "Alex", voice = "actor").validated() }.isFailure)
        for (speed in listOf(0.24, 1.51, Double.NaN)) assertTrue(runCatching { AssistantProfile(name = "Alex", speed = speed).validated() }.isFailure)
        val request = JSONObject(RealtimeSettings.secretRequest(AssistantProfile(name = "Jarvis")))
        assertEquals(60, request.getJSONObject("expires_after").getInt("seconds"))
        assertTrue(request.getJSONObject("session").getString("instructions").contains("Jarvis"))
        assertFalse(request.toString().contains("backend"))
    }
    @Test fun `frases ignoran mayusculas tildes y una diferencia en nombres largos`() {
        for ((heard, name) in listOf("ATENTO JARVIS" to "Jarvis", "atento álex" to "Alex", "Atento Yarvis" to "Jarvis", "atento pbtadmin" to "PbtAdmin", "Atento Friday" to "Friday", "Atento Cortana" to "Cortana")) assertTrue(heard, WakePhrase.matches(heard, name))
    }
    @Test fun `frases rechazan falsos positivos y documentan falsos negativos conservadores`() {
        for (heard in listOf("Jarvis", "atento", "no digas atento Jarvis", "atento Jarvis abre la cámara", "atenta Jarvis", "atento Friday", "atento marcos")) assertFalse(heard, WakePhrase.matches(heard, "Jarvis"))
        assertFalse(WakePhrase.matches("atento Alexa", "Alex"))
        assertFalse(WakePhrase.matches("a tento Jarvis", "Jarvis"))
        assertFalse(WakePhrase.matches("atento", ""))
    }
    @Test fun `AES GCM cifra autentica y usa IV distintos en cada sustitucion`() {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val other = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val first = "primera credencial ficticia".toByteArray()
        val second = "sustitucion ficticia".toByteArray()
        val a = KeyCipher.encrypt(key, first)
        val b = KeyCipher.encrypt(key, second)
        assertFalse(a.first.contentEquals(b.first))
        assertFalse(a.second.contentEquals(first))
        assertArrayEquals(second, KeyCipher.decrypt(key, b.first, b.second))
        assertTrue(runCatching { KeyCipher.decrypt(other, b.first, b.second) }.isFailure)
        b.second[0] = (b.second[0].toInt() xor 1).toByte()
        assertTrue(runCatching { KeyCipher.decrypt(key, b.first, b.second) }.isFailure)
    }
    @Test fun `errores de protocolo no repiten el cuerpo ni URLs con secretos`() {
        val secret = "sk-" + "z".repeat(45)
        val failure = runCatching { RealtimeProtocol.secret(secret, 1) }.exceptionOrNull()!!
        assertFalse(failure.message.orEmpty().contains(secret))
        assertFalse(runCatching { RealtimeProtocol.endpoint("https://dominio.es/$secret?[]") }.exceptionOrNull()?.message.orEmpty().contains(secret))
    }
    @Test fun `no habilita audio cuando servidor confirma otro perfil o voz`() {
        val profile = AssistantProfile(name = "Jarvis", voice = "cedar", speed = 0.9)
        val applied = RealtimeSettings.session(profile)
        assertTrue(RealtimeSettings.confirms(applied, profile))
        assertFalse(RealtimeSettings.confirms(null, profile))
        assertFalse(RealtimeSettings.confirms(applied, profile.copy(name = "Alex")))
        assertFalse(RealtimeSettings.confirms(applied, profile.copy(voice = "marin")))
        assertFalse(RealtimeSettings.confirms(applied, profile.copy(speed = 1.0)))
        applied.put("model", "otro-modelo")
        assertFalse(RealtimeSettings.confirms(applied, profile))
    }
}
