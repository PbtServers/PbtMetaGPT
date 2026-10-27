package com.pbtservers.pbtmetagpt

import android.app.ActivityManager
import android.content.*
import android.os.*
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class PcmAudio(val bytes: ByteArray, val sampleRate: Int)

class LocalModelClient(private val context: Context) {
    suspend fun synthesize(store: VoicePackageStore, id: String, text: String, speed: Double): PcmAudio = withTimeout(60000) {
        val pack = withContext(Dispatchers.IO) { store.describe(id) }
        val info = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        require(Build.SUPPORTED_ABIS.any { it in setOf("arm64-v8a", "x86_64") } && info.totalMem / 1048576 >= pack.minRamMb && !info.lowMemory) { "Este dispositivo no cumple la arquitectura o memoria requerida por el modelo." }
        val phonemes = withContext(Dispatchers.IO) { PronunciationLexicon.load(store.file(id, "lexicon.tsv"), store.file(id, "tokens.txt")).phonemes(text) }
        val pipe = ParcelFileDescriptor.createPipe()
        val model = ParcelFileDescriptor.open(store.file(id, "model.onnx"), ParcelFileDescriptor.MODE_READ_ONLY)
        val tokens = ParcelFileDescriptor.open(store.file(id, "tokens.txt"), ParcelFileDescriptor.MODE_READ_ONLY)
        val ready = CompletableDeferred<Int>(); val finished = CompletableDeferred<Unit>()
        var remote: Messenger? = null
        var bound = false
        val handler = Handler(Looper.getMainLooper()) { msg ->
            when (msg.what) { 1 -> ready.complete(msg.arg1); 3 -> finished.complete(Unit); 4 -> { val failure = IllegalStateException("El runtime aislado rechazó el modelo o la síntesis."); ready.completeExceptionally(failure); finished.completeExceptionally(failure) } }
            true
        }
        val reply = Messenger(handler)
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                try {
                    remote = Messenger(binder)
                    remote!!.send(Message.obtain(null, 1).apply {
                        replyTo = reply
                        data = Bundle().apply { putParcelable("model", model); putParcelable("tokens", tokens); putParcelable("audio", pipe[1]); putIntArray("phonemes", phonemes); putFloat("speed", speed.toFloat()) }
                    })
                    // Binder duplicó los descriptores. Cerrar el escritor local permite detectar EOF real.
                    pipe[1].close()
                } catch (_: Exception) { ready.completeExceptionally(IllegalStateException("No se pudo iniciar el motor aislado.")); finished.completeExceptionally(IllegalStateException("Fallo de síntesis aislada.")) }
            }
            override fun onServiceDisconnected(name: ComponentName) {
                if (!finished.isCompleted) {
                    val failure = IllegalStateException("El proceso del modelo se cerró sin completar la síntesis.")
                    ready.completeExceptionally(failure); finished.completeExceptionally(failure)
                }
            }
            override fun onNullBinding(name: ComponentName) = onServiceDisconnected(name)
            override fun onBindingDied(name: ComponentName) = onServiceDisconnected(name)
        }
        try {
            bound = context.bindIsolatedService(Intent(context, ModelVoiceService::class.java), Context.BIND_AUTO_CREATE,
                "voz_" + java.util.UUID.randomUUID().toString().replace("-", ""), context.mainExecutor, connection)
            check(bound) { "Android no permitió iniciar el motor aislado." }
            val rate = ready.await()
            val bytes = suspendCancellableCoroutine<ByteArray> { continuation ->
                continuation.invokeOnCancellation {
                    runCatching { remote?.send(Message.obtain(null, 2)) }
                    runCatching { pipe[0].close() }
                }
                Thread({ try { ParcelFileDescriptor.AutoCloseInputStream(pipe[0]).use { input ->
                    val output = ByteArrayOutputStream(); val buffer = ByteArray(16384)
                    while (continuation.isActive) { val n = input.read(buffer); if (n < 0) break; require(output.size() + n <= 16 * 1024 * 1024) { "La salida de audio supera el límite de memoria." }; output.write(buffer, 0, n) }
                    if (continuation.isActive) continuation.resume(output.toByteArray())
                } } catch (_: Exception) { if (continuation.isActive) continuation.resumeWithException(IllegalStateException("No se pudo leer el audio del motor aislado.")) }
                }, "leer-audio-aislado").start()
            }
            finished.await()
            require(bytes.isNotEmpty() && bytes.size % 2 == 0) { "El modelo no generó audio PCM válido." }
            PcmAudio(bytes, rate)
        } finally {
            runCatching { remote?.send(Message.obtain(null, 2)) }
            handler.removeCallbacksAndMessages(null)
            if (bound) context.unbindService(connection)
            listOf(model, tokens, pipe[0], pipe[1]).forEach { runCatching { it.close() } }
        }
    }
}
