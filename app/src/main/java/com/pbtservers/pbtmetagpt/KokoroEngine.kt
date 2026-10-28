package com.pbtservers.pbtmetagpt

import android.content.Context
import android.app.ActivityManager
import com.k2fsa.sherpa.onnx.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.ByteArrayOutputStream

/** Un modelo en memoria por sesión; CPU, dos hilos, sin red ni Android TextToSpeech. */
class KokoroEngine(private val context: Context) {
    private val mutex = Mutex()
    private var loaded: OfflineTts? = null
    private var vocabulary = emptySet<Int>()
    suspend fun synthesize(profile: AssistantProfile, text: String, firstPcm: () -> Unit = {}): PcmAudio = withContext(Dispatchers.Default) {
        mutex.withLock {
            require(profile.ttsEngine == "kokoro" && profile.language.startsWith("es") && text.length in 1..1000)
            val sid = requireNotNull(KokoroCatalog.voices[profile.ttsVoice]) { "Voz Kokoro no compatible." }
            val store = KokoroModel(context)
            if (loaded == null) {
                val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
                check(!memory.lowMemory && memory.availMem > 600L * 1024 * 1024) { "Memoria disponible insuficiente para Kokoro." }
                check(store.verify()) { "Instala o verifica Kokoro en Voces → Natural." }
                val root = store.directory
                vocabulary = KokoroCatalog.tokens.lineSequence().filter { it.isNotBlank() }.map { it.substringBeforeLast(' ').ifEmpty { " " }.codePointAt(0) }.toSet() + 32
                loaded = OfflineTts(config = OfflineTtsConfig(model = OfflineTtsModelConfig(
                    kokoro = OfflineTtsKokoroModelConfig(model = File(root, "model.onnx").path,
                        voices = File(root, "voices.bin").path, tokens = File(root, "tokens.txt").path),
                    numThreads = 2, debug = false, provider = "cpu")))
            }
            val engine = requireNotNull(loaded)
            check(engine.numSpeakers() == 54 && engine.sampleRate() == 24000) { "Contrato Kokoro inesperado." }
            val ipa = KokoroText.phonemes(text) { LocalPhonemes.convert(store.directory.path, it) }
            val phones = ipa.codePoints().toArray()
            require(phones.isNotEmpty() && phones.all { it in vocabulary }) { "El fonemizador devolvió símbolos no compatibles con Kokoro." }
            val sentences = mutableListOf<IntArray>(); var offset = 0
            while (offset < phones.size) {
                var end = minOf(offset + 400, phones.size)
                if (end < phones.size) end = (end - 1 downTo offset + 1).firstOrNull { phones[it] == 32 }?.plus(1)
                    ?: throw IllegalArgumentException("Palabra demasiado larga para Kokoro.")
                sentences += phones.copyOfRange(offset, end); offset = end
            }
            val job = currentCoroutineContext().job
            val output = ByteArrayOutputStream()
            var overflow = false
            engine.generateWithConfigAndCallback("", GenerationConfig(speed = profile.speed.toFloat(), sid = sid,
                phonemeCodepoints = sentences.toTypedArray()), object : (FloatArray) -> Int {
                @androidx.annotation.Keep override fun invoke(samples: FloatArray): Int {
                    if (!job.isActive) return 0
                    if (output.size() + samples.size * 2L > 2_880_000) { overflow = true; return 0 }
                    if (output.size() == 0 && samples.isNotEmpty()) firstPcm()
                    samples.forEach { sample ->
                        val value = (sample.coerceIn(-1f, 1f) * 32767).toInt()
                        output.write(value and 255); output.write((value shr 8) and 255)
                    }
                    return 1
                }
            })
            ensureActive()
            check(!overflow) { "Audio Kokoro fuera del límite de un minuto por fragmento." }
            require(output.size() > 0) { "Kokoro no generó audio." }
            PcmAudio(output.toByteArray(), 24000)
        }
    }
    suspend fun close() = withContext(Dispatchers.Default + NonCancellable) {
        mutex.withLock {
            if (loaded != null) { loaded?.release(); LocalPhonemes.release() }
            loaded = null; vocabulary = emptySet()
        }
    }
}
