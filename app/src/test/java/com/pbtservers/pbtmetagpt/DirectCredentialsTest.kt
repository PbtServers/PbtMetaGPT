package com.pbtservers.pbtmetagpt

import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DirectCredentialsTest {
    @Test fun `BYOK no necesita URL servidor ni token y solo envia clave al host oficial`() {
        val key = "sk-" + "f".repeat(40)
        val request = DirectCredentials.request(key, AssistantProfile(name = "Jarvis", voice = "cedar"))
        assertEquals("https://api.openai.com/v1/realtime/client_secrets", request.url.toString())
        assertEquals("POST", request.method)
        assertEquals("Bearer $key", request.header("Authorization"))
        assertFalse(request.url.toString().contains(key))
        val body = Buffer().also { request.body!!.writeTo(it) }.readUtf8()
        assertFalse(body.contains(key))
        assertEquals("cedar", JSONObject(body).getJSONObject("session").getJSONObject("audio").getJSONObject("output").getString("voice"))
    }
    @Test fun `primera comprobacion no requiere imponer un nombre`() {
        val request = DirectCredentials.request("sk-" + "f".repeat(40), null)
        val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertFalse(body.getJSONObject("session").has("instructions"))
        assertEquals("gpt-realtime", body.getJSONObject("session").getString("model"))
    }
    @Test fun `errores directos nunca propagan credenciales ni cuerpos del proveedor`() {
        val private = "sk-" + "x".repeat(40)
        for (code in listOf(400, 401, 403, 429, 500)) {
            val failure = runCatching { DirectCredentials.decode(HttpReply(code, private), 1000) }.exceptionOrNull() as DiagnosticFailure
            assertEquals("OPENAI_HTTP_$code", failure.code)
            assertFalse(failure.message.orEmpty().contains(private))
        }
        assertTrue(runCatching { DirectCredentials.request("token-backend", null) }.isFailure)
        assertEquals("ek_credencial_ficticia", DirectCredentials.decode(HttpReply(200, """{"value":"ek_credencial_ficticia","expires_at":1060}"""), 1000))
        assertTrue(runCatching { DirectCredentials.decode(HttpReply(200, """{"value":"ek_caducada","expires_at":1001}"""), 1000) }.isFailure)
    }
}
