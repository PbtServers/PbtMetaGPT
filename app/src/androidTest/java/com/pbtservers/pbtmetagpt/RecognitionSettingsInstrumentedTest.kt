package com.pbtservers.pbtmetagpt

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class RecognitionSettingsInstrumentedTest {
    @Test fun cancelarUnaDescargaBorraLaPreparacionYPermiteReintentarSinActivarMicrofono() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = WakeModel(context)
        model.delete()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val manager = WakeModelDownload(context, scope)
        val reading = CompletableDeferred<Unit>()
        try {
            manager.install {
                object : java.io.FilterInputStream(instrumentation.context.assets.open("activacion-prueba.zip")) {
                    override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                        reading.complete(Unit)
                        Thread.sleep(20)
                        return super.read(bytes, offset, length)
                    }
                }
            }
            withTimeout(5000) { reading.await() }
            manager.cancel()
            withTimeout(5000) { while (manager.state.value.downloading) delay(20) }
            assertFalse(model.installed())
            assertTrue(manager.state.value.status.contains("cancelada"))
            manager.install { instrumentation.context.assets.open("activacion-prueba.zip") }
            withTimeout(20000) { while (!manager.state.value.installed) { assertTrue(manager.state.value.downloading); delay(20) } }
            assertNull((context.applicationContext as PbtApplication).microphone.owner())
        } finally { scope.cancel(); model.cancelDownload(); model.delete() }
    }
    @Test fun googleNavegadorYBusquedaConstruyenEntradasPublicasSinAbrirElAsistente() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launcher = WebLauncher(context)
        val query = "qué tiempo hace en Puigcerdà"
        val search = launcher.intent(WebAction(WebDestination.SEARCH, query))
        if (search != null) {
            assertTrue(search.action in setOf(android.content.Intent.ACTION_WEB_SEARCH, android.content.Intent.ACTION_VIEW))
            assertNotEquals(android.content.Intent.ACTION_ASSIST, search.action)
            assertNotEquals(android.content.Intent.ACTION_VOICE_COMMAND, search.action)
            if (search.action == android.content.Intent.ACTION_WEB_SEARCH) assertEquals(query, search.getStringExtra(android.app.SearchManager.QUERY))
            else assertEquals(query, search.data?.getQueryParameter("q"))
        }
        launcher.intent(WebAction(WebDestination.BROWSER))?.let {
            assertEquals(android.content.Intent.ACTION_VIEW, it.action)
            assertEquals("https", it.data?.scheme)
        }
        launcher.intent(WebAction(WebDestination.GOOGLE))?.let {
            assertEquals(SystemAssistantLauncher.GOOGLE, it.component?.packageName)
            assertEquals(android.content.Intent.ACTION_MAIN, it.action)
        }
    }
    @Test fun motorYCalidadPersistenYAutorizarRedNoEquivaleAAbrirMicrofono() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = HostedSettings(context)
        val oldMode = settings.recognitionMode
        val oldSource = settings.dictationSource
        val oldAuthorization = settings.groqAudioAuthorized
        val app = context.applicationContext as PbtApplication
        try {
            settings.recognitionMode(RecognitionMode.SPEED)
            settings.authorizeGroqAudio(true)
            assertEquals(DictationSource.GROQ_WHISPER, HostedSettings(context).dictationSource)
            settings.dictationSource(DictationSource.VOSK)
            assertEquals(DictationSource.VOSK, HostedSettings(context).dictationSource)
            assertEquals(RecognitionMode.SPEED, HostedSettings(context).recognitionMode)
            assertNull(app.microphone.owner())
            assertFalse(ContinuousWakeService.running)
        } finally {
            settings.authorizeGroqAudio(oldAuthorization)
            settings.dictationSource(oldSource)
            settings.recognitionMode(oldMode)
        }
    }
    @Test fun archivoIncorrectoSeRechazaYNoInvalidaUnModeloVerificado() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val model = WakeModel(context)
        model.delete()
        try {
            try { model.install(source = { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }); fail("Debe rechazar SHA incorrecto") }
            catch (_: IllegalArgumentException) { }
            assertFalse(model.installed())
            val progress = mutableListOf<Long>()
            model.install(progress = { bytes, _, _ -> progress += bytes }, source = { instrumentation.context.assets.open("activacion-prueba.zip") })
            assertTrue(model.installed())
            assertTrue(progress.size > 2)
            assertTrue(progress.zipWithNext().all { (first, next) -> first <= next })
            model.install(source = { error("Un modelo válido no se vuelve a descargar") })
            assertEquals(WakeModel.SHA256, model.digest())
            val marker = java.io.File(model.model, "instalado.sha256")
            marker.writeText("archivo inválido")
            assertFalse(model.installed())
            model.install(source = { instrumentation.context.assets.open("activacion-prueba.zip") })
            assertTrue(model.installed())
        } finally { model.delete() }
    }
}
