package com.pbtservers.pbtmetagpt

import android.content.Context
import ai.onnxruntime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Piper Davefx es_ES real sobre el ORT Android ya utilizado por RVC. Sin Android TTS ni red. */
class SpainVoiceEngine(private val context: Context) {
    private val mutex = Mutex()
    private var session: OrtSession? = null
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    suspend fun synthesize(profile: AssistantProfile, text: String): PcmAudio = withContext(Dispatchers.Default) {
        mutex.withLock {
            require(profile.ttsEngine == "piper_es" && profile.language == "es-ES" && text.length in 1..1000)
            val store = SpainVoiceModel(context)
            if (session == null) {
                val memory = android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
                check(!memory.lowMemory && memory.availMem > 256L * 1024 * 1024) { "No hay memoria suficiente para cargar la voz de España." }
                check(store.verify()) { "Descarga la voz de España en Voces → Natural." }
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                    options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
                    session = environment.createSession(File(store.directory, SpainVoiceCatalog.resource.name).path, options)
                }
            }
            val loaded = requireNotNull(session)
            require(loaded.inputNames == setOf("input", "input_lengths", "scales")) { "Contrato Piper no compatible." }
            val phones = KokoroText.raw(text) { LocalPhonemes.convert(store.directory.path, it) }
            val ids = SpainVoiceCatalog.ids(phones)
            val inputs = mutableMapOf<String, OnnxTensor>()
            try {
                inputs["input"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
                inputs["input_lengths"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
                inputs["scales"] = OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(.667f, (1 / profile.speed).toFloat(), .8f)), longArrayOf(3))
                OrtSession.RunOptions().use { options ->
                    val cancellation = launch(Dispatchers.IO) { try { awaitCancellation() } finally { runCatching { options.setTerminate(true) } } }
                    try {
                        loaded.run(inputs, options).use { result ->
                            ensureActive()
                            val tensor = result[0] as OnnxTensor
                            require((tensor.info as TensorInfo).type == OnnxJavaType.FLOAT)
                            val floats = tensor.floatBuffer
                            require(floats.remaining() in 1..1_323_000) { "Piper supera un minuto por fragmento." }
                            val bytes = ByteArray(floats.remaining() * 2)
                            for (i in bytes.indices step 2) {
                                val value = (floats.get().coerceIn(-1f, 1f) * 32767).toInt()
                                bytes[i] = value.toByte(); bytes[i + 1] = (value shr 8).toByte()
                            }
                            PcmAudio(bytes, 22050)
                        }
                    } finally { cancellation.cancelAndJoin() }
                }
            } finally { inputs.values.forEach { it.close() }; ids.fill(0) }
        }
    }
    suspend fun close() = withContext(Dispatchers.Default + NonCancellable) {
        mutex.withLock {
            if (session != null) { session?.close(); session = null; LocalPhonemes.release() }
        }
    }
}
