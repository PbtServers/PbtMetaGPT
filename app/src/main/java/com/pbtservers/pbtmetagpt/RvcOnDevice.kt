package com.pbtservers.pbtmetagpt

import ai.onnxruntime.OnnxJavaType
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.util.Half
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.Closeable
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.ShortBuffer
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Conversor RVC offline: PCM -> HuBERT -> RMVPE -> RVC Synth -> PCM.
 * Exclusivamente para modelos ONNX exportados en el formato voice-changer.
 *
 * No carga .pth con pickle, no utiliza servidores y no copia código de
 * otras implementaciones. Los pesos se proporcionan por el usuario.
 */
class RvcOnDevice(private val store: RvcOnnxStore) : Closeable {
    private val env = OrtEnvironment.getEnvironment()
    private val sessions = mutableListOf<OrtSession>()
    private val synth: OrtSession
    private val hubert: OrtSession
    private val pitch: OrtSession
    private val outputRate: Int
    private val hubertOutput: String
    private val channels: Int
    private val usesPitch: Boolean

    init {
        try {
            val (synthFile, hubertFile, pitchFile) = store.files()
            fun load(path: String): OrtSession {
                val session = env.createSession(path, OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(2)
                })
                sessions.add(session)
                return session
            }
            synth = load(synthFile.absolutePath)
            val raw = synth.metadata.customMetadata["metadata"]
                ?: throw IllegalArgumentException("El sintetizador ONNX no contiene metadatos RVC voice-changer.")
            val meta = JSONObject(raw)
            outputRate = meta.getInt("samplingRate")
            require(outputRate in listOf(32000, 40000, 44100, 48000)) {
                "Frecuencia RVC ONNX no compatible."
            }
            usesPitch = meta.getBoolean("f0")
            channels = meta.getInt("embChannels")
            require(channels in setOf(256, 768)) { "Dimensión HuBERT incompatible." }
            hubertOutput = when {
                meta.getInt("embOutputLayer") == 9 && meta.optBoolean("useFinalProj") -> "units9"
                meta.getInt("embOutputLayer") == 12 && meta.optBoolean("useFinalProj") -> "unit12s"
                meta.getInt("embOutputLayer") == 12 -> "unit12"
                else -> throw IllegalArgumentException("Extractor HuBERT no compatible.")
            }
            require(synth.inputInfo.keys.containsAll(setOf("feats", "p_len", "sid")) && "audio" in synth.outputInfo) {
                "Entradas o salidas del sintetizador no compatibles."
            }
            if (usesPitch) require(synth.inputInfo.keys.containsAll(setOf("pitch", "pitchf")))
            hubert = load(hubertFile.absolutePath)
            require("audio" in hubert.inputInfo && hubertOutput in hubert.outputInfo) {
                "Modelo HuBERT incompatible con el sintetizador."
            }
            pitch = load(pitchFile.absolutePath)
            if (usesPitch) require(pitch.inputInfo.keys.containsAll(setOf("waveform", "threshold")))
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    suspend fun convert(audio: PcmAudio): PcmAudio = withContext(Dispatchers.Default) {
        require(audio.sampleRate in 8000..48000 && audio.bytes.size % 2 == 0)
        require(audio.bytes.size in 3200..audio.sampleRate * 2 * 8) {
            "RVC admite fragmentos de voz de 0,1 a 8 segundos."
        }
        val source = FloatArray(audio.bytes.size / 2) { i ->
            val lo = audio.bytes[2 * i].toInt() and 255
            val hi = audio.bytes[2 * i + 1].toInt()
            ((hi shl 8) or lo).toShort().toInt() / 32768f
        }
        val sixteenK = if (audio.sampleRate == 16000) source else FloatArray(
            (source.size.toLong() * 16000 / audio.sampleRate).toInt()
        ) { idx ->
            val pos = idx.toDouble() * audio.sampleRate / 16000
            val low = pos.toInt().coerceIn(0, source.lastIndex)
            val next = (low + 1).coerceAtMost(source.lastIndex)
            val frac = (pos - low).toFloat()
            source[low] * (1f - frac) + source[next] * frac
        }
        require(sixteenK.size in 1600..128000)
        val embed = runFloat(hubert, mapOf("audio" to floatTensor(sixteenK, 1L, sixteenK.size.toLong())), hubertOutput)
        val shape = embed.second
        require(shape.size == 3 && shape[0] == 1L && shape[2] == channels.toLong() && shape[1] in 1..1200)
        val t = shape[1].toInt()
        val frames = embed.first
        require(frames.size == t * channels)
        val upsampled = FloatArray(t * 2 * channels)
        for (frame in 0 until t) {
            System.arraycopy(frames, frame * channels, upsampled, frame * 2 * channels, channels)
            System.arraycopy(frames, frame * channels, upsampled, (frame * 2 + 1) * channels, channels)
        }
        val f0 = if (usesPitch) {
            runFloat(pitch, mapOf(
                "waveform" to floatTensor(sixteenK, 1L, sixteenK.size.toLong()),
                "threshold" to floatTensor(floatArrayOf(0.3f), 1L)
            )).first
        } else null
        val frameCount = minOf(t * 2, f0?.size ?: Int.MAX_VALUE)
        require(frameCount > 0)
        val features = if (frameCount == t * 2) upsampled else upsampled.copyOf(frameCount * channels)
        val inputs = mutableMapOf<String, OnnxTensor>()
        try {
            inputs["feats"] = featureTensor(features, 1L, frameCount.toLong(), channels.toLong())
            inputs["p_len"] = longTensor(longArrayOf(frameCount.toLong()), 1L)
            inputs["sid"] = longTensor(longArrayOf(0L), 1L)
            if (usesPitch) {
                val pitchFloat = requireNotNull(f0).copyOf(frameCount)
                val coarse = LongArray(frameCount) { index ->
                    val hz = pitchFloat[index]
                    if (hz <= 0 || !hz.isFinite()) 1L
                    else {
                        val mel = 1127.0 * ln(1.0 + hz / 700.0)
                        val lowMel = 1127.0 * ln(1.0 + 50.0 / 700.0)
                        val highMel = 1127.0 * ln(1.0 + 1100.0 / 700.0)
                        (1.0 + (mel - lowMel) * 254.0 / (highMel - lowMel))
                            .coerceIn(1.0, 255.0).roundToInt().toLong()
                    }
                }
                inputs["pitch"] = longTensor(coarse, 1L, frameCount.toLong())
                inputs["pitchf"] = floatTensor(pitchFloat, 1L, frameCount.toLong())
            }
            val samples = runFloat(synth, inputs, "audio", closeInputs = false).first
            require(samples.isNotEmpty() && samples.size <= outputRate * 20)
            val pcm = ByteArray(samples.size * 2)
            samples.forEachIndexed { i, sample ->
                val value = (sample.coerceIn(-1f, 1f) * 32767).roundToInt().toShort().toInt()
                pcm[2 * i] = value.toByte()
                pcm[2 * i + 1] = (value ushr 8).toByte()
            }
            PcmAudio(pcm, outputRate)
        } finally {
            inputs.values.forEach { it.close() }
        }
    }

    private fun floatTensor(data: FloatArray, vararg dimensions: Long) =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(data), dimensions)
    private fun longTensor(data: LongArray, vararg dimensions: Long) =
        OnnxTensor.createTensor(env, LongBuffer.wrap(data), dimensions)
    private fun featureTensor(data: FloatArray, vararg dimensions: Long): OnnxTensor {
        val kind = (synth.inputInfo["feats"]?.info as? TensorInfo)?.type
        return if (kind == OnnxJavaType.FLOAT16) {
            // Los bits IEEE 754 FP16 se almacenan en short; evitar devolver
            // valores @HalfFloat desde una lambda genérica ShortArray(size).
            val halfBits = ShortArray(data.size)
            for (index in data.indices) {
                halfBits[index] = Half.toHalf(data[index])
            }
            OnnxTensor.createTensor(env, ShortBuffer.wrap(halfBits),
                dimensions, OnnxJavaType.FLOAT16)
        } else {
            require(kind == OnnxJavaType.FLOAT) { "Tensor RVC incompatible." }
            floatTensor(data, *dimensions)
        }
    }

    private fun runFloat(session: OrtSession, inputs: Map<String, OnnxTensor>, output: String? = null,
        closeInputs: Boolean = true):
        Pair<FloatArray, LongArray> {
        val ownInputs = inputs.values.toList()
        try {
            val results = if (output == null) session.run(inputs) else session.run(inputs, setOf(output))
            results.use {
                val tensor = it.iterator().next().value as? OnnxTensor
                    ?: throw IllegalArgumentException("La salida del modelo no es un tensor ONNX.")
                val kind = (tensor.info as TensorInfo).type
                val numbers = when (kind) {
                    OnnxJavaType.FLOAT -> {
                        val buffer = tensor.floatBuffer
                        FloatArray(buffer.remaining()).also { array -> buffer.get(array) }
                    }
                    OnnxJavaType.FLOAT16 -> {
                        val buffer = tensor.byteBuffer.asShortBuffer()
                        FloatArray(buffer.remaining()) { index -> Half.toFloat(buffer.get(index)) }
                    }
                    else -> throw IllegalArgumentException("Tipo de salida ONNX RVC no compatible.")
                }
                return numbers to (tensor.info as TensorInfo).shape
            }
        } finally {
            if (closeInputs) ownInputs.forEach { it.close() }
        }
    }

    override fun close() {
        sessions.asReversed().forEach { runCatching { it.close() } }
        sessions.clear()
    }
}
