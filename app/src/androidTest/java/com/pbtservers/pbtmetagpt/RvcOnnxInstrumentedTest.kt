package com.pbtservers.pbtmetagpt

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.UUID

/**
 * Ejecución nativa real de tres grafos ONNX sintéticos: demuestra compatibilidad
 * de JNI, importación y tubería, NO inteligencia acústica de un modelo RVC real.
 */
class RvcOnnxInstrumentedTest {
    @Test fun conviertePcmLocalmenteSinRedConTresGrafosSinteticos() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = UUID.randomUUID().toString()
        val sandbox = File(context.cacheDir, "rvc-smoke-" + id)
        require(sandbox.mkdirs())
        val prefsPrefix = "rvc-smoke-" + id + "-"
        val isolated = object : ContextWrapper(context) {
            override fun getFilesDir(): File = sandbox
            override fun getSharedPreferences(name: String, mode: Int) =
                context.getSharedPreferences(prefsPrefix + name, mode)
        }
        val store = RvcOnnxStore(isolated)
        try {
            val models = listOf("synth", "hubert", "rmvpe").associateWith {
                context.assets.open("rvc_smoke/" + it + ".onnx")
            }
            store.install(models)
            assertTrue(store.ready())
            assertFalse(store.enabled)
            store.setEnabled(true)
            assertTrue(store.enabled)
            val runtime = RvcOnDevice(store)
            try {
                val result = withTimeout(45000) { runtime.convert(PcmAudio(ByteArray(32000), 16000)) }
                assertEquals(40000, result.sampleRate)
                assertEquals(32, result.bytes.size)
            } finally { runtime.close() }
        } finally {
            store.clear()
            sandbox.deleteRecursively()
            context.deleteSharedPreferences(prefsPrefix + "rvc_onnx")
        }
    }
}
