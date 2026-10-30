package com.pbtservers.pbtmetagpt

import kotlin.math.sqrt

/** VAD energético adaptativo, independiente de la ganancia absoluta del micrófono.
 * No identifica al hablante y no sustituye a un detector neuronal de ruido/voz.
 */
class AdaptiveVad(private val rate: Int = 16000, private val endSilenceMs: Int = 1100, private val adaptiveEnding: Boolean = false) {
    enum class Event { WAIT, START, VOICE, PAUSE, END }
    private var floor = 80.0
    private var attack = 0
    private var silence = 0
    var voicedSamples = 0
        private set
    var started = false
        private set
    fun accept(samples: ShortArray, count: Int = samples.size): Event {
        require(count in 1..samples.size)
        val rms = sqrt((0 until count).sumOf { samples[it].toDouble() * samples[it] } / count)
        val voice = rms > maxOf(35.0, floor * 2.8)
        if (!voice && !started) floor = (floor * 0.90 + rms * 0.10).coerceIn(12.0, 2000.0)
        if (voice) {
            attack += count; silence = 0; voicedSamples += count
            if (!started && attack >= rate * 60 / 1000) { started = true; return Event.START }
            return if (started) Event.VOICE else Event.WAIT
        }
        attack = 0
        if (!started) return Event.WAIT
        silence += count
        // En dictado preciso dar 300 ms adicionales a intervenciones cortas: una
        // primera palabra seguida de pausa no debe parecer una pregunta completa.
        val ending = endSilenceMs + if (adaptiveEnding && voicedSamples < rate) 300 else 0
        return if (silence >= rate * ending / 1000) Event.END else Event.PAUSE
    }
    fun enoughVoice() = voicedSamples >= rate * 120 / 1000
}

/** Búfer circular anterior al inicio: 300 ms, únicamente en RAM y borrable. */
class PcmPreRoll(private val capacity: Int = 4800) {
    private val data = ShortArray(capacity)
    private var size = 0
    private var cursor = 0
    fun append(samples: ShortArray, count: Int = samples.size) {
        require(count in 0..samples.size)
        for (i in 0 until count) { data[cursor] = samples[i]; cursor = (cursor + 1) % capacity; size = minOf(size + 1, capacity) }
    }
    fun snapshot(): ShortArray = ShortArray(size) { data[(cursor - size + capacity + it) % capacity] }
    fun clear() { data.fill(0); size = 0; cursor = 0 }
}
