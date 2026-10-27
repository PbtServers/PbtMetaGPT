package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class VoiceSelectionTest {
    @Test fun todasLasVocesOficialesSeleccionanGuardanYRestauran() {
        for (engine in listOf("realtime", "openai_tts")) {
            for (voice in VoiceSelection.catalog(engine)) {
                val original = AssistantProfile(name = "Prueba", personality = "Clara", ttsEngine = engine)
                val changed = VoiceSelection.official(original, voice).validated()
                val restored = AssistantProfile.import(AssistantProfile.export(listOf(changed))).single()
                assertEquals(voice, restored.voice)
                assertEquals(original.id, restored.id)
                assertEquals("Clara", restored.personality)
                assertEquals(engine, restored.ttsEngine)
            }
        }
    }
    @Test fun pasarDeSpeechARealtimeNormalizaVocesExclusivas() {
        for (voice in listOf("fable", "onyx", "nova")) {
            val speech = AssistantProfile(name = "Prueba", ttsEngine = "openai_tts", voice = voice)
            assertEquals("marin", VoiceSelection.engine(speech, "realtime").validated().voice)
        }
    }
    @Test fun pulsarElMotorActivoConservaSuVozLocal() {
        val local = AssistantProfile(name = "Prueba", ttsEngine = "android", ttsVoice = "es-local", ttsPackage = "org.example.tts", pitch = 1.3)
        assertEquals(local, VoiceSelection.engine(local, "android"))
        assertEquals("es-local", local.validated().ttsVoice)
    }
    @Test(expected = IllegalArgumentException::class) fun rechazaVozDeOtroMotor() {
        VoiceSelection.official(AssistantProfile(name = "Prueba"), "nova")
    }
    @Test fun borradorIncompletoConservaTodosSusCamposSinValidarseComoPerfil() {
        val draft = AssistantProfile(name = "J", personality = "Analítica", voice = "cedar", ttsEngine = "external")
        assertEquals(draft, AssistantProfile.parse(JSONObject(draft.json().toString()), validate = false))
    }
}
