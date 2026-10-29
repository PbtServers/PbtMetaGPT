package com.pbtservers.pbtmetagpt

import android.content.Context
import ai.onnxruntime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Un ONNX activo. Cambiar de hablante Sharvard reutiliza sesión, pesos y fonemizador. */
class SpainVoiceEngine(private val context: Context, private val reproducibleFixture: Boolean = false) {
    private val mutex = Mutex()
    private var session: OrtSession? = null
    private var loadedPackage: String? = null
    private var vocabulary = SpainVoiceCatalog.phonemes
    internal var loadCount = 0; private set
    private val environment by lazy { OrtEnvironment.getEnvironment() }
    suspend fun synthesize(profile: AssistantProfile, text: String): PcmAudio = withContext(Dispatchers.Default) {
        mutex.withLock {
            require(profile.ttsEngine == "piper_es" && profile.language == "es-ES" && text.length in 1..1000)
            val voice = SpainVoiceCatalog.voice(profile.ttsVoice)
            require(profile.ttsPackage == voice.packageId)
            val store = SpainVoiceModel(context, voice.id)
            if (session == null || loadedPackage != voice.packageId) {
                session?.close(); session = null; loadedPackage = null
                val memory = android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }
                check(!memory.lowMemory && memory.availMem > 256L * 1024 * 1024) { "No hay memoria suficiente para cargar la voz de España." }
                check(store.verify()) { "Descarga la voz de España en Voces → Natural." }
                vocabulary = if (voice.packageId == SpainVoiceCatalog.sharvardPackage) {
                    val config = org.json.JSONObject(File(store.directory, SpainVoiceCatalog.sharvardConfig.name).readText())
                    require(config.getJSONObject("language").getString("code") == "es_ES" && config.getJSONObject("audio").getInt("sample_rate") == 22050)
                    require(config.getJSONObject("espeak").getString("voice") == "es" && config.getInt("num_speakers") == 2)
                    val speakers = config.getJSONObject("speaker_id_map")
                    require(speakers.getInt("M") == 0 && speakers.getInt("F") == 1)
                    val map = config.getJSONObject("phoneme_id_map")
                    map.keys().asSequence().associate { phone ->
                        require(phone.codePointCount(0, phone.length) == 1 && map.getJSONArray(phone).length() == 1)
                        phone.codePointAt(0) to map.getJSONArray(phone).getLong(0)
                    }
                } else SpainVoiceCatalog.phonemes
                val modelResource = if (voice.packageId == SpainVoiceCatalog.sharvardPackage) SpainVoiceCatalog.sharvardResource else SpainVoiceCatalog.resource
                OrtSession.SessionOptions().use { options ->
                    options.setIntraOpNumThreads(2); options.setInterOpNumThreads(1)
                    options.setSessionLogLevel(OrtLoggingLevel.ORT_LOGGING_LEVEL_ERROR)
                    session = environment.createSession(File(store.directory, modelResource.name).path, options)
                    loadedPackage = voice.packageId; loadCount++
                }
            }
            val loaded = requireNotNull(session)
            require(loaded.inputNames == if (voice.speakerId == null) setOf("input", "input_lengths", "scales") else setOf("input", "input_lengths", "scales", "sid")) { "Contrato Piper no compatible." }
            val phones = KokoroText.raw(SpanishSpeechText.pronunciation(text)) { LocalPhonemes.convert(store.directory.path, it) }
            val ids = SpainVoiceCatalog.ids(phones, vocabulary)
            val inputs = mutableMapOf<String, OnnxTensor>()
            try {
                inputs["input"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong()))
                inputs["input_lengths"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(ids.size.toLong())), longArrayOf(1))
                voice.speakerId?.let { sid -> inputs["sid"] = OnnxTensor.createTensor(environment, LongBuffer.wrap(longArrayOf(sid)), longArrayOf(1)) }
                // Una fixture PCM necesita señal reproducible: el ruido aleatorio
                // modifica vocales/duración de una palabra corta entre ejecuciones.
                // La app conserva los parámetros originales; solo tests usan cero.
                inputs["scales"] = OnnxTensor.createTensor(environment, FloatBuffer.wrap(floatArrayOf(
                    if (reproducibleFixture) 0f else .667f, (1 / profile.speed).toFloat(), if (reproducibleFixture) 0f else .8f)), longArrayOf(3))
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
            loadedPackage = null; vocabulary = SpainVoiceCatalog.phonemes
        }
    }
}
