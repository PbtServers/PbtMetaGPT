package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class VoiceMigrationTest {
    @Test fun naturalPuedeReutilizarVozOfflineSinCambiarAProveedorRemoto() {
        val offline = AssistantProfile(name = "Luna", ttsEngine = "android", ttsVoice = "es_ES_neuronal",
            ttsPackage = "org.example.offlinetts", voiceMode = "rapida")
        val natural = VoiceModes.choose(offline, "natural").validated()
        assertEquals("natural", natural.voiceMode)
        assertEquals("android", natural.ttsEngine)
        assertEquals(offline.ttsVoice, natural.ttsVoice)
        assertEquals(offline.ttsPackage, natural.ttsPackage)
        assertEquals("qwen_space", VoiceModes.choose(AssistantProfile(name = "Luna"), "natural").ttsEngine)
        val online = VoiceModes.qwen(natural, "Ryan").validated()
        assertEquals("qwen_space", online.ttsEngine)
        assertEquals("0.6B", online.ttsPackage)
    }
    @Test fun rapidaDesdeUnaVozExclusivaNoArrastraUnaConfiguracionIncompatible() {
        val special = AssistantProfile(name = "Luna", ttsEngine = "openai_tts", voice = "onyx", voiceMode = "personalizada")
        val rapid = VoiceModes.choose(special, "rapida").validated()
        assertEquals("rapida", rapid.voiceMode)
        assertEquals(special.name, rapid.name)
        assertTrue(rapid.voice in RealtimeSettings.voices)
        assertEquals(special.activationPhrases, rapid.activationPhrases)
    }
    @Test fun perfilesAntiguosConservanMotorVozYModeloSinImponerRapida() {
        val original = AssistantProfile(name = "Luna", ttsEngine = "qwen_space", ttsVoice = "Ryan", ttsPackage = "1.7B")
        val old = original.json().apply { remove("voiceMode") }
        val migrated = AssistantProfile.parse(old)
        assertEquals("natural", migrated.voiceMode)
        assertEquals("Ryan", migrated.ttsVoice); assertEquals("1.7B", migrated.ttsPackage)
        val oldRealtime = AssistantProfile(name = "Luna", voice = "cedar").json().apply { remove("voiceMode") }
        assertEquals("personalizada", AssistantProfile.parse(oldRealtime).voiceMode)
        assertEquals("cedar", AssistantProfile.parse(oldRealtime).voice)
    }
    @Test fun perfilesNuevosEmpiezanEnRapidaYSuModoSeExporta() {
        val profile = AssistantProfile(name = "Luna")
        assertEquals("rapida", profile.voiceMode)
        assertEquals(profile, AssistantProfile.import(AssistantProfile.export(listOf(profile))).single())
    }
    @Test fun streamingNoMuestraRazonamientoNiMetadatos() {
        assertEquals("Hola", GroqStream.content("""{"choices":[{"delta":{"content":"Hola","reasoning":"interno"}}]}"""))
        assertEquals("", GroqStream.content("""{"choices":[],"usage":{"total_tokens":20}}"""))
    }
}
