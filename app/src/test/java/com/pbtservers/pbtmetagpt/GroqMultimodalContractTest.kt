package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GroqMultimodalContractTest {
    @Test fun imagenSeEnviaAlModeloVisionSinOpenAiYCopiaPrivada() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"choices":[{"message":{"content":"Hay una bicicleta junto al camino."}}]}"""))
            val image = ByteArray(200).apply { this[0] = 0xff.toByte(); this[1] = 0xd8.toByte() }
            val api = GroqApi(OkHttpClient(), server.url("/openai/v1/").toString())
            assertEquals("Hay una bicicleta junto al camino.", api.analyzeImage("token-de-prueba-no-real", image))
            val req = server.takeRequest()
            assertEquals("/openai/v1/chat/completions", req.path)
            assertEquals("Bearer token-de-prueba-no-real", req.getHeader("Authorization"))
            val body = JSONObject(req.body.readUtf8())
            assertEquals("qwen/qwen3.8-27b", body.getString("model"))
            val items = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
            assertEquals("image_url", items.getJSONObject(1).getString("type"))
            assertTrue(items.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
        }
    }
    @Test fun whisperSubeWavEnMultipartConIdiomaEspanolSoloAlSolicitarse() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json")
                .setBody("""{"text":"Abre los ajustes del asistente."}"""))
            val wav = WavAudio.encodePcm16Mono(16000, ByteArray(32000))
            val api = GroqApi(OkHttpClient(), server.url("/openai/v1/").toString())
            assertEquals("Abre los ajustes del asistente.", api.transcribe("token-de-prueba-no-real", wav))
            val req = server.takeRequest()
            assertEquals("/openai/v1/audio/transcriptions", req.path)
            assertTrue(req.getHeader("Content-Type").orEmpty().startsWith("multipart/form-data;"))
            val text = req.body.readByteArray().toString(Charsets.ISO_8859_1)
            assertTrue(text.contains("whisper-large-v3-turbo"))
            assertTrue(text.contains("grabacion.wav"))
            assertTrue(text.contains("name=\"language\""))
            assertTrue(text.contains("\r\n\r\nes\r\n"))
            assertTrue(text.contains("name=\"prompt\""))
            assertFalse(req.path.orEmpty().contains("translations"))
        }
    }
    @Test fun modoPrecisoEnviaLargeV3ConIdiomaFijoSinDeteccionAutomatica() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"text":"Quiero ir a Puigcerdà."}"""))
            val api = GroqApi(OkHttpClient(), server.url("/openai/v1/").toString())
            assertEquals("Quiero ir a Puigcerdà.", api.transcribe("token-de-prueba-no-real", WavAudio.encodePcm16Mono(16000, ByteArray(32000)), RecognitionPolicy.whisperModel(RecognitionMode.PRECISION)))
            val req = server.takeRequest()
            val body = req.body.readUtf8()
            assertTrue(body.contains("\r\n\r\nwhisper-large-v3\r\n"))
            assertTrue(body.contains("\r\n\r\nes\r\n"))
            assertTrue(body.contains("Google, Spotify, ChatGPT"))
            assertEquals("/openai/v1/audio/transcriptions", req.path)
        }
    }
}
