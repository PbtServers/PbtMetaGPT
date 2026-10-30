package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/** Un único modelo oficial, sin importar código ni modelos arbitrarios al proceso con credenciales. */
class WakeModel(context: Context) {
    private val root = File(context.noBackupFilesDir, "activacion-vosk")
    val model = File(root, NAME)
    fun installed() = runCatching { digest() == SHA256 && required.all { File(model, it).length() > 0 } }.getOrDefault(false)
    fun digest() = File(model, "instalado.sha256").takeIf { it.isFile }?.readText().orEmpty()
    fun delete() { model.deleteRecursively() }
    @Volatile private var download: okhttp3.Call? = null
    fun cancelDownload() { download?.cancel() }
    suspend fun install(progress: (Long, Long, String) -> Unit = { _, _, _ -> }, source: (() -> java.io.InputStream)? = null): Unit = installLock.withLock {
        if (installed()) return@withLock
        root.mkdirs()
        require(root.usableSpace >= 320L * 1024 * 1024) { "Necesitas 320 MiB libres para instalar el modelo." }
        val staging = File(root, "descarga-temporal"); staging.deleteRecursively(); staging.mkdirs()
        val archive = File(staging, "modelo.zip")
        try {
            if (source != null) source().use { input -> archive.outputStream().use { output ->
                val buffer = ByteArray(32768); var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val n = input.read(buffer); if (n < 0) break
                    total += n; require(total <= 64L * 1024 * 1024)
                    output.write(buffer, 0, n)
                    progress(total, -1, "Descargando modelo")
                }
            } } else {
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).callTimeout(3, TimeUnit.MINUTES).build()
            val call = client.newCall(Request.Builder().url(URL).build()); download = call
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Descarga no disponible")
                val body = response.body ?: throw IOException("Descarga vacía")
                body.byteStream().use { input -> archive.outputStream().use { output ->
                    val buffer = ByteArray(32768); var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val size = input.read(buffer); if (size < 0) break
                        total += size; require(total <= 64L * 1024 * 1024) { "Archivo demasiado grande" }
                        output.write(buffer, 0, size)
                        progress(total, body.contentLength(), "Descargando modelo")
                    }
                } }
            }
            }
            progress(archive.length(), archive.length(), "Verificando SHA-256")
            val expected = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input -> val buffer = ByteArray(32768); while (true) {
                val n = input.read(buffer); if (n < 0) break; expected.update(buffer, 0, n)
            } }
            require(expected.digest().joinToString("") { "%02x".format(it) } == SHA256) { "Hash del modelo Vosk incorrecto" }
            progress(archive.length(), archive.length(), "Instalando modelo español")
            val unpacked = File(staging, NAME); unpacked.mkdirs()
            var expanded = 0L; val seen = mutableSetOf<String>()
            ZipInputStream(archive.inputStream().buffered()).use { zip ->
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val entry = zip.nextEntry ?: break
                    require(entry.name.startsWith("$NAME/")) { "Raíz de modelo inesperada" }
                    if (entry.isDirectory) continue
                    val path = entry.name.removePrefix("$NAME/")
                    require(path in allowed && seen.add(path)) { "Contenido de modelo inesperado" }
                    val target = File(unpacked, path); target.parentFile?.mkdirs()
                    target.outputStream().use { output ->
                        val buffer = ByteArray(32768)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val size = zip.read(buffer); if (size < 0) break
                            expanded += size; require(expanded <= 256L * 1024 * 1024) { "Modelo demasiado grande" }
                            output.write(buffer, 0, size)
                        }
                    }
                }
            }
            require(required.all { File(unpacked, it).length() > 0 }) { "Modelo incompleto" }
            val hash = MessageDigest.getInstance("SHA-256")
            archive.inputStream().use { input -> val buffer = ByteArray(32768); while (true) {
                val size = input.read(buffer); if (size < 0) break; hash.update(buffer, 0, size)
            } }
            File(unpacked, "instalado.sha256").writeText(hash.digest().joinToString("") { "%02x".format(it) })
            currentCoroutineContext().ensureActive()
            val backup = File(root, "modelo-anterior")
            backup.deleteRecursively()
            if (model.exists()) check(model.renameTo(backup)) { "No se pudo conservar el modelo anterior" }
            if (!unpacked.renameTo(model)) {
                if (backup.exists()) check(backup.renameTo(model))
                error("No se pudo instalar el modelo")
            }
            backup.deleteRecursively()
        } finally { download = null; staging.deleteRecursively() }
    }
    companion object {
        private val installLock = Mutex()
        const val NAME = "vosk-model-small-es-0.42"
        const val URL = "https://alphacephei.com/vosk/models/vosk-model-small-es-0.42.zip"
        const val SHA256 = "09b239888f633ef2f0b4e09736e3d9936acfd810bc65d53fad45261762c6511f"
        private val required = setOf("am/final.mdl", "conf/mfcc.conf", "conf/model.conf", "graph/Gr.fst", "graph/HCLr.fst", "graph/phones/word_boundary.int")
        private val allowed = required + setOf("README", "graph/disambig_tid.int", "graph/words.txt", "ivector/final.dubm", "ivector/final.ie", "ivector/final.mat", "ivector/global_cmvn.stats", "ivector/online_cmvn.conf", "ivector/splice.conf")
    }
}
