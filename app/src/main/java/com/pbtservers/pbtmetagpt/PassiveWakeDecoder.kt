package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import java.io.Closeable

/** Misma canalización en servicio y fixtures; sin ASR de silencio ni audio persistente. */
class PassiveWakeDecoder(model: Model, rate: Int = 16000) : Closeable {
    private val speech = PassiveSpeechGate(rate)
    private val recognizer = Recognizer(model, rate.toFloat()).apply { setMaxAlternatives(5); setWords(true) }
    private var timelineOffset = 0L
    val startFrame get() = timelineOffset
    internal var inferenceFrames = 0L; private set
    fun accept(samples: ShortArray, count: Int): JSONObject? {
        return when (speech.accept(samples, count)) {
            PassiveSpeechGate.Event.WAIT -> null
            PassiveSpeechGate.Event.START -> {
                recognizer.reset()
                // Reset nativo conserva su reloj. Compensar solo el silencio no inferido.
                timelineOffset = speech.startFrame - inferenceFrames
                val prefix = speech.takePrefix()
                try { decode(prefix, prefix.size) } finally { prefix.fill(0) }
            }
            PassiveSpeechGate.Event.FEED -> decode(samples, count)
            PassiveSpeechGate.Event.END -> {
                inferenceFrames += count
                JSONObject(if (recognizer.acceptWaveForm(samples, count)) recognizer.result else recognizer.finalResult)
            }
        }
    }
    private fun decode(samples: ShortArray, count: Int): JSONObject? {
        inferenceFrames += count
        return if (recognizer.acceptWaveForm(samples, count)) JSONObject(recognizer.result) else null
    }
    override fun close() { try { recognizer.close() } finally { speech.clear() } }
}
