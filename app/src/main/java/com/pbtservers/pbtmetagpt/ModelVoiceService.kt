package com.pbtservers.pbtmetagpt

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process
import com.k2fsa.sherpa.onnx.*
import java.io.FileOutputStream
import android.system.Os
import android.system.OsConstants

/** JNI de modelos de usuario en UID aislado, sin Internet, Keystore ni acceso a datos de la app. */
class ModelVoiceService : Service() {
    // Reabrir /proc/self/fd de un archivo privado de otro UID puede ser rechazado.
    // Copiar sus datos autorizados a memfd propio conserva el aislamiento sin abrir permisos del archivo.
    private fun memoryCopy(source: ParcelFileDescriptor, limit: Long): ParcelFileDescriptor {
        val descriptor = Os.memfd_create("voz_datos", OsConstants.MFD_CLOEXEC)
        val reader = try { ParcelFileDescriptor.dup(descriptor) } catch (problem: Exception) { Os.close(descriptor); throw problem }
        try {
            FileOutputStream(descriptor).use { output -> ParcelFileDescriptor.AutoCloseInputStream(source).use { input ->
                val buffer = ByteArray(65536); var count = 0L
                while (true) { val n = input.read(buffer); if (n < 0) break; count += n; require(count <= limit); output.write(buffer, 0, n) }
            } }
            return reader
        } catch (problem: Exception) { reader.close(); throw problem }
    }
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == 2) { Process.killProcess(Process.myPid()); return }
            if (message.what != 1) return
            val data = message.data
            val reply = message.replyTo ?: return
            Thread({
                var engine: OfflineTts? = null
                var modelMemory: ParcelFileDescriptor? = null
                var tokensMemory: ParcelFileDescriptor? = null
                val model = data.getParcelable<ParcelFileDescriptor>("model")
                val tokens = data.getParcelable<ParcelFileDescriptor>("tokens")
                val audio = data.getParcelable<ParcelFileDescriptor>("audio")
                try {
                    require(model != null && tokens != null && audio != null)
                    val modelData = memoryCopy(model, 268435456); modelMemory = modelData
                    val tokenData = memoryCopy(tokens, 2097152); tokensMemory = tokenData
                    val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                        model = "/proc/self/fd/${modelData.fd}", tokens = "/proc/self/fd/${tokenData.fd}"), numThreads = 2, debug = false, provider = "cpu"))
                    val loaded = OfflineTts(config = config); engine = loaded
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
                } catch (_: Throwable) { runCatching { reply.send(Message.obtain(null, 4)) } }
                finally {
                    runCatching { engine?.release() }; runCatching { audio?.close() }; runCatching { model?.close() }; runCatching { tokens?.close() }
                    runCatching { modelMemory?.close() }; runCatching { tokensMemory?.close() }
                    // Proceso por petición: elimina estado nativo y cualquier grafo que no coopere tras cancelar.
                    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100)
                }
            }, "sintesis-aislada").start()
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
}
