package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*

class SpainVoiceInstrumentedTest {
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
