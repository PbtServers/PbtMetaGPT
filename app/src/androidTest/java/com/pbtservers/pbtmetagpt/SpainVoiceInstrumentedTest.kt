package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class SpainVoiceInstrumentedTest {
    @Test fun sharvardTieneDosPcmDistintosPersisteYReutilizaElMismoModelo() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = SpainVoiceModel(context, "sharvard_m")
        val engine = SpainVoiceEngine(context, reproducibleFixture = true)
        val hashes = mutableSetOf<String>()
        try {
            store.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            assertTrue(store.verify())
            for (id in listOf("sharvard_m", "sharvard_f")) {
                val profile = VoiceModes.spain(AssistantProfile(name = "Luna"), id).validated()
                ProfileStore(context).save(listOf(profile), profile.id)
                assertEquals(profile, ProfileStore(context).load().single())
                val pcm = engine.synthesize(profile, "En Zaragoza hay cinco plazas. Hablamos en español de España.")
                try {
                    assertEquals(22050, pcm.sampleRate)
                    assertTrue(pcm.bytes.size > 4410)
                    assertTrue(pcm.bytes.any { it.toInt() != 0 })
                    hashes += java.security.MessageDigest.getInstance("SHA-256").digest(pcm.bytes).joinToString("") { "%02x".format(it) }
                    assertEquals(1, engine.loadCount)
                    assertEquals(store.directory, SpainVoiceModel(context, id).directory)
                    android.util.Log.i("PbtKokoroTest", "Sharvard sid ${SpainVoiceCatalog.voice(id).speakerId}; PCM ${pcm.bytes.size} bytes; PSS ${android.os.Debug.getPss()} KiB; emulador")
                } finally { pcm.bytes.fill(0) }
            }
            assertEquals("El mismo texto debe producir voces diferentes sin ruido aleatorio", 2, hashes.size)
        } finally { engine.close(); store.delete(); ProfileStore(context).save(emptyList(), null) }
    }
    @Test fun generaPcmConModeloRealEsEspanaYPersisteLaSeleccion() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val store = SpainVoiceModel(context)
        val engine = SpainVoiceEngine(context)
        try {
            store.delete()
            store.install(source = { instrumentation.context.assets.open("piper-es/${it.name}") })
            assertTrue(store.verify())
            val profile = VoiceModes.spain(AssistantProfile(name = "Luna")).validated()
            ProfileStore(context).save(listOf(profile), profile.id)
            assertEquals(profile, ProfileStore(context).load().single())
            val start = android.os.SystemClock.elapsedRealtime()
            val audio = engine.synthesize(profile, "En Zaragoza hay cinco plazas. ¿Podéis acercaros vosotros?")
            assertEquals(22050, audio.sampleRate)
            assertTrue(audio.bytes.size > 4410)
            assertTrue(audio.bytes.any { it.toInt() != 0 })
            android.util.Log.i("PbtKokoroTest", "Piper es-ES frío: ${android.os.SystemClock.elapsedRealtime() - start} ms; PCM: ${audio.bytes.size} bytes; PSS: ${android.os.Debug.getPss()} KiB; emulador")
            audio.bytes.fill(0)
            val warm = android.os.SystemClock.elapsedRealtime()
            assertTrue(engine.synthesize(profile, "Buenos días. Hablamos en español de España.").bytes.isNotEmpty())
            android.util.Log.i("PbtKokoroTest", "Piper es-ES caliente: ${android.os.SystemClock.elapsedRealtime() - warm} ms; emulador")
        } finally { engine.close(); store.delete(); ProfileStore(context).save(emptyList(), null) }
    }
}
