package com.pbtservers.pbtmetagpt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class VoiceInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun modeloRealGeneraPcmEnProcesoAisladoSinAcreditarInteligibilidad() = runBlocking {
        val root = File(context.filesDir, "voces-prueba")
        val store = VoicePackageStore(root)
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        try {
            val pack = withContext(Dispatchers.IO) { assets.open("modelo-prueba.zip").use { store.install(it) } }
            val audio = withContext(Dispatchers.Main) { LocalModelClient(context, publicFixtureDiagnostics = true).synthesize(store, pack.id, pack.sampleText, 1.0) }
            assertTrue(audio.sampleRate in 8000..48000)
            assertTrue(audio.bytes.size > audio.sampleRate / 5)
            assertTrue(audio.bytes.any { it.toInt() != 0 })
            store.delete(pack.id)
            assertTrue(store.list().isEmpty())
        } finally { root.deleteRecursively() }
    }
    @Test fun credencialesTtsYOpenaiSeAislanYNoSeExportanConPerfil() {
        val openai = KeyVault(context); val external = KeyVault(context, externalTts = true)
        val apiKey = "sk-" + "q".repeat(40); val token = "proveedor-" + "z".repeat(30)
        try {
            openai.save(apiKey); external.save(token)
            assertEquals(apiKey, openai.read()); assertEquals(token, external.read())
            val profile = AssistantProfile(name = "Jarvis", ttsEngine = "external", ttsVoice = "voz-autorizada")
            val export = AssistantProfile.export(listOf(profile))
            assertFalse(export.contains(apiKey)); assertFalse(export.contains(token)); assertFalse(export.contains("endpoint"))
            external.delete(); assertFalse(external.exists()); assertEquals(apiKey, openai.read())
        } finally { openai.delete(); external.delete() }
    }
}
