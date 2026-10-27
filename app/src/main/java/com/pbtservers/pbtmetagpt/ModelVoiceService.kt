package com.pbtservers.pbtmetagpt

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process
import com.k2fsa.sherpa.onnx.*
import android.content.res.AssetFileDescriptor
import android.content.res.Configuration
import android.content.res.loader.AssetsProvider
import android.content.res.loader.ResourcesLoader
import android.content.res.loader.ResourcesProvider
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit

/** JNI de modelos de usuario en UID aislado, sin Internet, Keystore ni acceso a datos de la app. */
class ModelVoiceService : Service() {
    private fun <T> onMain(block: () -> T): T {
        if (Looper.myLooper() == Looper.getMainLooper()) return block()
        val task = FutureTask<T> { block() }
        Handler(Looper.getMainLooper()).post(task)
        return task.get(5, TimeUnit.SECONDS)
    }
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == 2) { Process.killProcess(Process.myPid()); return }
            if (message.what != 1) return
            val data = message.data
            val reply = message.replyTo ?: return
            Thread({
                var engine: OfflineTts? = null
                var loader: ResourcesLoader? = null
                var provider: ResourcesProvider? = null
                var modelResources: android.content.res.Resources? = null
                val model = data.getParcelable<ParcelFileDescriptor>("model")
                val tokens = data.getParcelable<ParcelFileDescriptor>("tokens")
                val audio = data.getParcelable<ParcelFileDescriptor>("audio")
                var stage = 1
                try {
                    require(model != null && tokens != null && audio != null)
                    stage = 2
                    require(model.statSize in 1L..268435456L && tokens.statSize in 1L..2097152L)
                    // API pública: el runtime lee datos por AssetManager, sin reabrir rutas /proc
                    // prohibidas al UID aislado y sin conceder acceso al directorio privado.
                    val loadedResources = onMain {
                        val assetProvider = ResourcesProvider.empty(object : AssetsProvider {
                            override fun loadAssetFd(path: String, accessMode: Int): AssetFileDescriptor? {
                                val source = when (path) {
                                    "assets/model.onnx", "model.onnx" -> model
                                    "assets/tokens.txt", "tokens.txt" -> tokens
                                    else -> return null
                                }
                                return AssetFileDescriptor(ParcelFileDescriptor.dup(source.fileDescriptor), 0, source.statSize)
                            }
                        })
                        provider = assetProvider
                        val assetLoader = ResourcesLoader().apply { addProvider(assetProvider) }; loader = assetLoader
                        createConfigurationContext(Configuration(resources.configuration)).resources.also { modelResources = it; it.addLoaders(assetLoader) }
                    }
                    stage = 3
                    val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                        model = "model.onnx", tokens = "tokens.txt"), numThreads = 2, debug = false, provider = "cpu"))
                    stage = 4
                    val loaded = OfflineTts(assetManager = loadedResources.assets, config = config); engine = loaded
                    val rate = loaded.sampleRate(); require(rate in 8000..48000)
                    reply.send(Message.obtain(null, 1).apply { arg1 = rate })
                    val phones = requireNotNull(data.getIntArray("phonemes"))
                    val sentences = mutableListOf<IntArray>()
                    var start = 0
                    while (start < phones.size) {
                        var end = minOf(start + 256, phones.size)
                        if (end < phones.size) {
                            val boundary = (end - 1 downTo start + 1).firstOrNull { phones[it] == 32 }
                            if (boundary != null) end = boundary + 1
                        }
                        sentences += phones.copyOfRange(start, end); start = end
                    }
                    val generation = GenerationConfig(speed = data.getFloat("speed", 1f), sid = 0, phonemeCodepoints = sentences.toTypedArray())
                    stage = 5
                    ParcelFileDescriptor.AutoCloseOutputStream(audio).use { output ->
                        var written = 0L
                        var failed = false
                        loaded.generateWithConfigAndCallback("", generation) { samples ->
                            try {
                                written += samples.size * 2L
                                require(written <= 16777216)
                                val bytes = ByteArray(samples.size * 2)
                                samples.forEachIndexed { i, sample ->
                                    val n = (sample.coerceIn(-1f, 1f) * 32767).toInt()
                                    bytes[i * 2] = n.toByte(); bytes[i * 2 + 1] = (n shr 8).toByte()
                                }
                                output.write(bytes); 1
                            } catch (_: Exception) { failed = true; 0 }
                        }
                        check(!failed) { "Síntesis cancelada o fuera de límite." }
                    }
                    reply.send(Message.obtain(null, 3))
                } catch (problem: Throwable) {
                    val type = Diagnostics.sdkCode(problem)
                    android.util.Log.e("PbtModelo", "Etapa $stage; tipo $type; mensaje original omitido")
                    // Exclusivo de la prueba con modelo público fijado: nunca se activa desde la interfaz ni para modelos del usuario.
                    if (BuildConfig.DEBUG && data.getBoolean("public_fixture_diagnostics")) android.util.Log.e("PbtFixture", problem.message.orEmpty().take(3000))
                    runCatching { reply.send(Message.obtain(null, 4).apply { arg1 = stage; this.data = Bundle().apply { putString("type", type) } }) }
                }
                finally {
                    runCatching { engine?.release() }; runCatching { audio?.close() }; runCatching { model?.close() }; runCatching { tokens?.close() }
                    runCatching { onMain { loader?.let { modelResources?.removeLoaders(it); it.clearProviders() }; provider?.close() } }
                    // Proceso por petición: elimina estado nativo y cualquier grafo que no coopere tras cancelar.
                    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100)
                }
            }, "sintesis-aislada").start()
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
}
