package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RealtimeProtocolTest {
    @Test fun `exige HTTPS y no permite secretos dentro de la URL`() {
        for (url in listOf("http://servidor.es", "https://usuario:clave@servidor.es", "https://servidor.es?token=x", "https://servidor.es#token", "archivo", "https:///")) {
            assertTrue(url, runCatching { RealtimeProtocol.endpoint(url) }.isFailure)
        }
        assertEquals("https://servidor.es/pbt/realtime/client-secret", RealtimeProtocol.endpoint(" https://servidor.es/pbt/ "))
    }
    @Test fun `solo admite credenciales temporales vigentes`() {
        val json = JSONObject().put("value", "ek_temporal_de_prueba").put("expires_at", 1060)
        assertEquals("ek_temporal_de_prueba", RealtimeProtocol.secret(json.toString(), 1000))
        for (secret in listOf("sk_privada", "", "sk-proj-privada")) {
            json.put("value", secret)
            assertTrue(runCatching { RealtimeProtocol.secret(json.toString(), 1000) }.isFailure)
        }
        json.put("value", "ek_temporal_de_prueba").put("expires_at", 1005)
        assertTrue(runCatching { RealtimeProtocol.secret(json.toString(), 1000) }.isFailure)
        assertTrue(runCatching { RealtimeProtocol.secret("{}", 1000) }.isFailure)
    }
    @Test fun `vision crea un mensaje de usuario con foto y pregunta explícita`() {
        val event = JSONObject(RealtimeProtocol.visualQuestion("Zm90bw=="))
        assertEquals("conversation.item.create", event.getString("type"))
        val item = event.getJSONObject("item")
        assertEquals("user", item.getString("role"))
        val content = item.getJSONArray("content")
        assertEquals("input_text", content.getJSONObject(0).getString("type"))
        assertTrue(content.getJSONObject(0).getString("text").contains("¿Qué estoy viendo?"))
        assertEquals("input_image", content.getJSONObject(1).getString("type"))
        assertEquals("data:image/jpeg;base64,Zm90bw==", content.getJSONObject(1).getString("image_url"))
    }
    @Test fun `interrupcion utiliza los eventos de Realtime GA`() {
        for (type in listOf("response.cancel", "output_audio_buffer.clear", "input_audio_buffer.clear")) {
            assertEquals(type, JSONObject(RealtimeProtocol.event(type)).getString("type"))
        }
    }
}
