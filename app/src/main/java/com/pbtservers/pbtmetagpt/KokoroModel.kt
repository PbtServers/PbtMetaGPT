package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import org.json.JSONObject

/** Descargas consentidas desde una revisión fija; publicación atómica tras comprobar todos los hashes. */
open class NeuralVoiceModel(private val context: Context, private val spec: NeuralVoiceSpec) {
    val directory = File(context.noBackupFilesDir, spec.id)
    fun installed() = File(directory, "verified").isFile && spec.resources.all { File(directory, it.name).length() == it.bytes } &&
        File(directory, "tokens.txt").isFile && File(directory, "phonemes-manifest.json").isFile
    suspend fun verify() = withContext(Dispatchers.IO) {
        try {
        installed() && spec.resources.all { KokoroCatalog.verify(File(directory, it.name), it) } && verifyPhonemes(directory) &&
            File(directory, "tokens.txt").readText() == spec.tokens
        } catch (_: IOException) { false }
        catch (_: org.json.JSONException) { false }
    }
    suspend fun delete() = withContext(Dispatchers.IO) { mutation.withLock { directory.deleteRecursively(); Unit } }
    suspend fun install(source: ((KokoroResource) -> java.io.InputStream)? = null, progress: (Long, Long) -> Unit = { _, _ -> }) = withContext(Dispatchers.IO) {
        mutation.withLock {
            if (verify()) return@withLock
            require(context.noBackupFilesDir.usableSpace > spec.freeMiB.toLong() * 1024 * 1024) { "No hay espacio suficiente para instalar la voz local." }
            val staging = File(context.noBackupFilesDir, "${spec.id}-download")
            staging.deleteRecursively(); staging.mkdirs()
            val total = spec.resources.sumOf { it.bytes }; var received = 0L; var lastPercent = -1L
            try {
                val client = OkHttpClient.Builder().callTimeout(15, TimeUnit.MINUTES).build()
                for (resource in spec.resources) {
                    val call = client.newCall(Request.Builder().url("${spec.source}${spec.revision}/${resource.name}").build())
                    val cancellation = launch { try { awaitCancellation() } finally { call.cancel() } }
                    try {
                        val response = if (source == null) call.execute() else null
                        try {
                            if (response != null) {
                                check(response.isSuccessful) { "Descarga Kokoro no disponible." }
                                require(response.request.url.isHttps) { "Descarga sin HTTPS rechazada." }
                            }
                            (source?.invoke(resource) ?: requireNotNull(response?.body).byteStream()).use { input -> File(staging, resource.name).outputStream().use { output ->
                                val buffer = ByteArray(65536); var count = 0L
                                while (true) {
                                    ensureActive(); val n = input.read(buffer); if (n < 0) break
                                    count += n; require(count <= resource.bytes) { "Tamaño Kokoro inesperado." }
                                    output.write(buffer, 0, n); received += n
                                    val percent = received * 100 / total
                                    if (percent != lastPercent) { lastPercent = percent; progress(received, total) }
                                }
                            } }
                        } finally { response?.close() }
                    } finally { cancellation.cancelAndJoin() }
                    require(KokoroCatalog.verify(File(staging, resource.name), resource)) { "SHA-256 de Kokoro incorrecto. Reintenta la descarga." }
                }
                File(staging, "tokens.txt").writeText(spec.tokens)
                val manifest = context.assets.open("phonemes-manifest.json").bufferedReader().use { it.readText() }
                val paths = JSONObject(manifest).keys().asSequence().toList()
                for (path in paths + "phonemes-manifest.json") {
                    require(!path.contains("..") && !path.startsWith('/'))
                    File(staging, path).also { it.parentFile?.mkdirs() }.outputStream().use { output -> context.assets.open(path).use { it.copyTo(output) } }
                }
                require(verifyPhonemes(staging)) { "Datos españoles incompletos." }
                File(staging, "verified").writeText(spec.revision)
                directory.deleteRecursively(); check(staging.renameTo(directory)) { "No se pudo publicar el modelo." }
            } finally { staging.deleteRecursively() }
        }
    }
    private fun verifyPhonemes(root: File): Boolean {
        val expected = context.assets.open("phonemes-manifest.json").bufferedReader().use { it.readText() }
        if (File(root, "phonemes-manifest.json").readText() != expected) return false
        val manifest = JSONObject(expected)
        val paths = manifest.keys().asSequence().toSet()
        if (!paths.containsAll(setOf("espeak-ng-data/phondata", "espeak-ng-data/phonindex", "espeak-ng-data/phontab", "espeak-ng-data/intonations", "espeak-ng-data/es_dict", "espeak-ng-data/lang/roa/es"))) return false
        return manifest.keys().asSequence().all { path ->
            !path.contains("..") && !path.startsWith('/') && File(root, path).isFile && KokoroCatalog.digest(File(root, path)) == manifest.getString(path)
        }
    }
    companion object { internal val mutation = Mutex() }
}

class KokoroModel(context: Context) : NeuralVoiceModel(context, KokoroCatalog.spec)
class SpainVoiceModel(context: Context, voice: String = "davefx") : NeuralVoiceModel(context, SpainVoiceCatalog.spec(voice))
