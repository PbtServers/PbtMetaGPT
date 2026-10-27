package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class VoiceMigrationTest {
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
