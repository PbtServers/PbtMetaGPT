package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** HTTP local real: comprueba el adaptador y el historial juntos, sin cuota ni credenciales reales. */
class GroqConversationContractTest {
    @Test fun veinteTurnosMantienenInstruccionesYContextoAcotadoEnLaPeticionReal() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val api = GroqApi(OkHttpClient(), server.url("/openai/v1/").toString())
            val history = ConversationHistory()
            repeat(20) { turn ->
                server.enqueue(MockResponse().setHeader("Content-Type", "text/event-stream")
                    .setBody("data: {\"choices\":[{\"delta\":{\"content\":\"Respuesta $turn\"}}]}\n\ndata: [DONE]\n\n"))
                history.user("Pregunta $turn")
                val response = withTimeout(3000) { api.answerStream("token-de-prueba-no-real", "openai/gpt-oss-20b",
                    listOf("system" to "Instrucción autorizada") + history.snapshot()) {} }
                assertEquals("Respuesta $turn", response)
                val messages = JSONObject(server.takeRequest().body.readUtf8()).getJSONArray("messages")
                assertEquals("system", messages.getJSONObject(0).getString("role"))
                assertEquals("Instrucción autorizada", messages.getJSONObject(0).getString("content"))
                assertEquals("Pregunta $turn", messages.getJSONObject(messages.length() - 1).getString("content"))
                assertTrue(messages.length() <= 33)
                if (turn >= 9) assertTrue("El adaptador debe aceptar más de 18 mensajes", messages.length() > 18)
                if (turn == 4) assertEquals("Pregunta 0", messages.getJSONObject(1).getString("content"))
                if (turn > 0) assertEquals("Respuesta ${turn - 1}", messages.getJSONObject(messages.length() - 2).getString("content"))
                history.assistant(response)
            }
        }
    }
    @Test fun limiteDeMensajesYCaracteresSigueBloqueandoEntradasExcesivas() = runBlocking<Unit> {
        MockWebServer().use { server ->
            val api = GroqApi(OkHttpClient(), server.url("/openai/v1/").toString())
            for (messages in listOf(List(34) { "user" to "Pregunta" }, listOf("system" to "Instrucción", "user" to "a".repeat(24001)))) {
                try { api.answerStream("token-de-prueba-no-real", "openai/gpt-oss-20b", messages) {}; fail("Debe conservar el límite de seguridad") }
                catch (_: IllegalArgumentException) { }
            }
            assertEquals(0, server.requestCount)
        }
    }
}
