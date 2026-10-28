package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Sin micrófono ni red: pesos reales verificados en el host, no un grafo sintético. */
class KokoroInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    @Test fun instalaVerificaGeneraAudioRealYLiberaAmbosRuntimes() = runBlocking {
        val store = KokoroModel(context)
        val engine = KokoroEngine(context)
        try {
            store.delete()
            assertFalse(store.installed())
            store.install(source = { instrumentation.context.assets.open("kokoro/${it.name}") })
            assertTrue(store.verify())
            val profile = VoiceModes.kokoro(AssistantProfile(name = "Luna"))
            ProfileStore(context).save(listOf(profile), profile.id)
            assertEquals(profile, ProfileStore(context).load().single())
            val start = android.os.SystemClock.elapsedRealtime()
            var firstPcm = -1L
            val audio = engine.synthesize(profile, "Hola. Esta es una prueba de voz en español.") { firstPcm = android.os.SystemClock.elapsedRealtime() - start }
            assertEquals(24000, audio.sampleRate)
            assertTrue(audio.bytes.size > 4800)
            assertTrue(audio.bytes.any { it.toInt() != 0 })
            assertTrue(firstPcm >= 0)
            // Cargar ORT Android después del JNI Sherpa: no acredita la calidad RVC.
            val ort = ai.onnxruntime.OrtEnvironment.getEnvironment()
            assertNotNull(ort)
            android.util.Log.i("PbtKokoroTest", "Primera PCM: $firstPcm ms; PCM: ${audio.bytes.size} bytes; PSS: ${android.os.Debug.getPss()} KiB; emulador")
            audio.bytes.fill(0)
            // Segundo fragmento reutiliza el motor, sin volver a descargar ni cargar pesos.
            val warm = android.os.SystemClock.elapsedRealtime()
            val second = engine.synthesize(profile.copy(ttsVoice = "em_alex"), "Buenos días. ¿Cómo puedo ayudarte?")
            assertTrue(second.bytes.isNotEmpty())
            android.util.Log.i("PbtKokoroTest", "Fragmento caliente: ${android.os.SystemClock.elapsedRealtime() - warm} ms; PCM: ${second.bytes.size} bytes; PSS: ${android.os.Debug.getPss()} KiB; emulador")
            second.bytes.fill(0)
            assertTrue(engine.synthesize(profile.copy(ttsVoice = "em_santa"), "Esta es la tercera voz española.").bytes.isNotEmpty())
            var completedAfterCancellation = false
            lateinit var synthesis: Job
            synthesis = launch(start = CoroutineStart.LAZY) {
                engine.synthesize(profile, "Esta prueba debe cancelar la síntesis y descartar el resultado antes de reproducir.") { synthesis.cancel() }
                completedAfterCancellation = true
            }
            synthesis.start(); synthesis.join()
            assertTrue(synthesis.isCancelled)
            assertFalse(completedAfterCancellation)
            engine.close()
            val model = File(store.directory, "model.onnx")
            java.io.RandomAccessFile(model, "rw").use { file -> val first = file.readByte(); file.seek(0); file.writeByte(first.toInt() xor 1) }
            assertFalse(store.verify())
            store.delete(); assertFalse(store.installed())
        } finally { engine.close(); store.delete(); ProfileStore(context).save(emptyList(), null) }
    }
    @Test fun cancelacionPreviaNoIniciaInferencia() = runBlocking {
        val engine = KokoroEngine(context)
        val work = launch(start = CoroutineStart.LAZY) { engine.synthesize(VoiceModes.kokoro(AssistantProfile(name = "Luna")), "Hola") }
        work.cancelAndJoin()
        assertTrue(work.isCancelled)
        engine.close()
    }
    @Test fun descargaTruncadaNoPublicaInstalacionYPermiteReintentar() = runBlocking {
        val store = KokoroModel(context)
        try {
            store.delete()
            assertTrue(runCatching { store.install(source = { byteArrayOf(1, 2, 3).inputStream() }) }.isFailure)
            assertFalse(store.installed())
            assertFalse(File(context.noBackupFilesDir, "kokoro-v1.0-download").exists())
        } finally { store.delete() }
    }
}
