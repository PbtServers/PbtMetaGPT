package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test
import okio.Buffer
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VoiceSynthesisTest {
    @Test fun `API externa usa contrato PCM sin reenviar la clave OpenAI`() {
        val profile = AssistantProfile(name = "Jarvis", ttsEngine = "external", ttsVoice = "original-autorizada")
        val request = VoiceSynthesisContract.request(profile, "Hola", "https://tts.example.com/v1/audio/speech", "modelo-propio", "token-externo")
        assertEquals("Bearer token-externo", request.header("Authorization"))
        val body = JSONObject(Buffer().also { request.body!!.writeTo(it) }.readUtf8())
        assertEquals("pcm", body.getString("response_format")); assertEquals("original-autorizada", body.getString("voice")); assertFalse(body.toString().contains("token-externo"))
        assertFalse(body.has("instructions"))
        for (url in listOf("http://tts.example.com", "https://usuario:clave@tts.example.com", "https://tts.example.com?token=x")) assertTrue(runCatching { ExternalVoiceConfig(url, "modelo").validate() }.isFailure)
    }
    @Test fun `voces nativas y alternativas se distinguen sin ejecucion implicita de modelos`() {
        assertFalse(VoiceSynthesisContract.alternative(AssistantProfile(name = "Jarvis")))
        for (engine in listOf("android", "openai_tts", "external", "sherpa_vits")) assertTrue(VoiceSynthesisContract.alternative(AssistantProfile(name = "Jarvis", ttsEngine = engine)))
        assertTrue(runCatching { AssistantProfile(name = "Jarvis", ttsEngine = "cargar-so").validated() }.isFailure)
        for (engine in listOf("android", "openai_tts", "external", "sherpa_vits")) {
            val p = AssistantProfile(name = "Jarvis", ttsEngine = engine)
            val session = RealtimeSettings.session(p)
            assertEquals("text", session.getJSONArray("output_modalities").getString(0))
            assertTrue(RealtimeSettings.confirms(session, p))
            session.put("output_modalities", org.json.JSONArray().put("audio"))
            assertFalse(RealtimeSettings.confirms(session, p))
        }
    }
    @Test fun `valida WAV mono PCM y rechaza formatos danados`() {
        val bytes = ByteArray(48)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put("RIFF".toByteArray()).putInt(40).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(24000).putInt(48000).putShort(2).putShort(16).put("data".toByteArray()).putInt(4).putInt(100)
        val pcm = VoiceSynthesisContract.wav(bytes)
        assertEquals(24000, pcm.sampleRate); assertEquals(4, pcm.bytes.size)
        buffer.putShort(22, 2); assertTrue(runCatching { VoiceSynthesisContract.wav(bytes) }.isFailure)
        assertTrue(runCatching { VoiceSynthesisContract.wav(byteArrayOf(1, 2)) }.isFailure)
    }
}
