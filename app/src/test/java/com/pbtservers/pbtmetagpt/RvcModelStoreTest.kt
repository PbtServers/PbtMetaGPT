package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RvcModelStoreTest {
    private fun archive(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { output ->
            ZipOutputStream(output).use { zip ->
                entries.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
        }.toByteArray()

    @Test fun importaPthEIndexComoDatosSinActivarInferencia() {
        val root = Files.createTempDirectory("rvc-models-").toFile()
        try {
            val library = RvcModelStore(root)
            val model = library.install("Voz Espanola.pth", byteArrayOf(1, 2, 3).inputStream())
            assertFalse(model.readyForInference)
            assertEquals(0L, model.indexBytes)
            library.attachIndex(model.id, byteArrayOf(4, 5).inputStream())
            assertEquals(2L, library.list().single().indexBytes)
            library.delete(model.id)
            assertTrue(library.list().isEmpty())
        } finally { root.deleteRecursively() }
    }

    @Test fun importaZipConCarpetaYRechazaRutasYPthDuplicados() {
        val root = Files.createTempDirectory("rvc-zip-").toFile()
        try {
            val library = RvcModelStore(root)
            val zip = archive("AuronPlay/model.pth" to byteArrayOf(1, 2), "AuronPlay/model.index" to byteArrayOf(3),
                "AuronPlay/README.txt" to "Datos".toByteArray())
            assertEquals(1L, library.installZip(zip.inputStream()).indexBytes)
            val malicious = listOf(
                archive("../escape.pth" to byteArrayOf(1)),
                archive("a.pth" to byteArrayOf(1), "b.pth" to byteArrayOf(2)),
                archive("model.exe" to byteArrayOf(1)),
                archive("model.index" to byteArrayOf(1))
            )
            for (bytes in malicious) assertTrue(runCatching { library.installZip(bytes.inputStream()) }.isFailure)
            assertEquals(1, library.list().size)
        } finally { root.deleteRecursively() }
    }

    @Test fun cancelaInstalacionSinDejarArchivos() {
        val root = Files.createTempDirectory("rvc-cancel-").toFile()
        try {
            val library = RvcModelStore(root)
            var calls = 0
            assertTrue(runCatching {
                library.install("a.pth", ByteArray(100000).inputStream()) {
                    if (++calls > 1) throw java.util.concurrent.CancellationException()
                }
            }.isFailure)
            assertTrue(root.listFiles().orEmpty().isEmpty())
        } finally { root.deleteRecursively() }
    }
}
