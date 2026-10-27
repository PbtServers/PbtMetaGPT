package com.pbtservers.pbtmetagpt

import android.app.Service
import android.content.Intent
import android.os.*
import android.os.Process
import com.k2fsa.sherpa.onnx.*
import java.io.FileOutputStream

/** JNI de modelos de usuario en UID aislado, sin Internet, Keystore ni acceso a datos de la app. */
class ModelVoiceService : Service() {
    private val messenger = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what == 2) { Process.killProcess(Process.myPid()); return }
            if (message.what != 1) return
            val data = message.data
            val reply = message.replyTo ?: return
            Thread({
                var engine: OfflineTts? = null
                val model = data.getParcelable<ParcelFileDescriptor>("model")
                val tokens = data.getParcelable<ParcelFileDescriptor>("tokens")
                val audio = data.getParcelable<ParcelFileDescriptor>("audio")
                try {
                    require(model != null && tokens != null && audio != null)
                    val config = OfflineTtsConfig(model = OfflineTtsModelConfig(vits = OfflineTtsVitsModelConfig(
                        model = "/proc/self/fd/${model.fd}", tokens = "/proc/self/fd/${tokens.fd}"), numThreads = 2, debug = false, provider = "cpu"))
                    val loaded = OfflineTts(config = config); engine = loaded
                    val rate = loaded.sampleRate(); require(rate in 8000..48000)
                    reply.send(Message.obtain(null, 1).apply { arg1 = rate })
                    val generation = GenerationConfig(speed = data.getFloat("speed", 1f), sid = 0,
                        phonemeCodepoints = arrayOf(requireNotNull(data.getIntArray("phonemes"))))
                    ParcelFileDescriptor.AutoCloseOutputStream(audio).use { output ->
                        loaded.generateWithConfigAndCallback("", generation) { samples ->
                            try {
                                val bytes = ByteArray(samples.size * 2)
                                samples.forEachIndexed { i, sample ->
                                    val n = (sample.coerceIn(-1f, 1f) * 32767).toInt()
                                    bytes[i * 2] = n.toByte(); bytes[i * 2 + 1] = (n shr 8).toByte()
                                }
                                output.write(bytes); 1
                            } catch (_: Exception) { 0 }
                        }
                    }
                    reply.send(Message.obtain(null, 3))
                } catch (_: Throwable) { runCatching { reply.send(Message.obtain(null, 4)) } }
                finally {
                    runCatching { engine?.release() }; runCatching { audio?.close() }; runCatching { model?.close() }; runCatching { tokens?.close() }
                    // Proceso por petición: elimina estado nativo y cualquier grafo que no coopere tras cancelar.
                    Handler(Looper.getMainLooper()).postDelayed({ Process.killProcess(Process.myPid()) }, 100)
                }
            }, "sintesis-aislada").start()
        }
    })
    override fun onBind(intent: Intent): IBinder = messenger.binder
}
