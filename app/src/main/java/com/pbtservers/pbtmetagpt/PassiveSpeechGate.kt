package com.pbtservers.pbtmetagpt

/** En silencio solo RMS/VAD y 300 ms circulares; Vosk recibe intervenciones completas. */
class PassiveSpeechGate(private val rate: Int = 16000) {
    enum class Event { WAIT, START, FEED, END }
    private var vad = AdaptiveVad(rate, endSilenceMs = 1300)
    private val preRoll = PcmPreRoll(rate * 300 / 1000)
    private var prefix = shortArrayOf()
    private var frames = 0L
    var startFrame = 0L; private set
    fun accept(samples: ShortArray, count: Int): Event {
        require(count in 1..samples.size)
        frames += count
        val wasStarted = vad.started
        if (!wasStarted) preRoll.append(samples, count)
        val event = vad.accept(samples, count)
        if (!wasStarted && vad.started) {
            prefix = preRoll.snapshot(); preRoll.clear()
            startFrame = frames - prefix.size
            return Event.START
        }
        if (vad.started && (event == AdaptiveVad.Event.END || frames - startFrame >= rate * 20)) {
            vad = AdaptiveVad(rate, endSilenceMs = 1300); preRoll.clear()
            return Event.END
        }
        return if (vad.started) Event.FEED else Event.WAIT
    }
    /** El consumidor borra esta única copia tras alimentar al reconocedor. */
    fun takePrefix(): ShortArray = prefix.also { prefix = shortArrayOf() }
    fun clear() { prefix.fill(0); prefix = shortArrayOf(); preRoll.clear(); vad = AdaptiveVad(rate, endSilenceMs = 1300) }
}
