package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.json.JSONArray
import org.vosk.Model
import org.vosk.Recognizer

/** Mismo decodificador para captura real y regresión de inferencia; un único hilo propietario. */
class LocalCommandDecoder(path: String, private val sampleRate: Float = 16000f,
    private val evaluation: ((CommandHypothesis, CommandHypothesis, SpeechBounds?) -> Unit)? = null,
    private val sharedModel: Model? = null) : AutoCloseable {
    private val model = sharedModel ?: Model(path)
    private fun closeModel() { if (sharedModel == null) model.close() }
    private var recognizer: Recognizer
    private var chat: Recognizer
    private var music: Recognizer
    private var vad = AdaptiveVad(sampleRate.toInt())
    private var samples = 0L
    private var inferenceFrames = 0L
    private var timelineOffset = 0L
    private val preRoll = PcmPreRoll(sampleRate.toInt() * 300 / 1000)
    private val replay = ShortArray(sampleRate.toInt() * 8)
    private var replaySize = 0
    private var voiceStart: Double? = null
    private var voiceEnd = 0.0
    init {
        val open = try { create(false) } catch (problem: Throwable) { closeModel(); throw problem }
        recognizer = open
        chat = try { create(true) } catch (problem: Throwable) { open.close(); closeModel(); throw problem }
        music = try { createMusic() } catch (problem: Throwable) { chat.close(); open.close(); closeModel(); throw problem }
    }
    private fun createMusic(): Recognizer {
        val value = Recognizer(model, sampleRate, JSONArray((MusicCommandGrammar.vocabulary + SystemAssistantCommandGrammar.vocabulary).distinct()).toString())
        try { value.setWords(true); value.setEndpointerMode(1); return value }
        catch (problem: Throwable) { value.close(); throw problem }
    }
    private fun create(restricted: Boolean): Recognizer {
        val value = if (restricted) Recognizer(model, sampleRate, JSONArray(ChatGptCommandGrammar.vocabulary).toString()) else Recognizer(model, sampleRate)
        try { value.setWords(true); value.setEndpointerMode(1); return value }
        catch (problem: Throwable) { value.close(); throw problem }
    }
    fun accept(buffer: ShortArray, size: Int): CommandHypothesis? {
        samples += size
        val wasStarted = vad.started
        if (!wasStarted) preRoll.append(buffer, size)
        val event = vad.accept(buffer, size)
        when (event) {
            AdaptiveVad.Event.START -> {
                voiceStart = (samples / sampleRate.toDouble() - .060).coerceAtLeast(0.0)
                voiceEnd = samples / sampleRate.toDouble()
                recognizer.reset(); chat.reset(); music.reset()
                replay.fill(0); replaySize = 0
                val prefix = preRoll.snapshot()
                timelineOffset = samples - prefix.size - inferenceFrames
                try { feed(prefix, prefix.size) } finally { prefix.fill(0); preRoll.clear() }
            }
            AdaptiveVad.Event.VOICE -> voiceEnd = samples / sampleRate.toDouble()
            else -> Unit
        }
        if (wasStarted) feed(buffer, size)
        // El endpoint nativo SHORT puede interpretar nombres desconocidos como
        // silencio y partir «Inicia ChatGPT en voz». Solo el VAD físico cierra.
        val maximum = voiceStart?.let { samples / sampleRate.toDouble() - it >= 20.0 } == true
        if (event != AdaptiveVad.Event.END && !maximum) return null
        return combine(recognizer.finalResult, chat.finalResult, music.finalResult)
    }
    private fun feed(buffer: ShortArray, size: Int) {
        if (replaySize >= 0 && replaySize + size <= replay.size) {
            buffer.copyInto(replay, replaySize, 0, size); replaySize += size
        } else { replay.fill(0); replaySize = -1 }
        inferenceFrames += size
        recognizer.acceptWaveForm(buffer, size)
        chat.acceptWaveForm(buffer, size)
        music.acceptWaveForm(buffer, size)
    }
    fun finish(): CommandHypothesis = combine(recognizer.finalResult, chat.finalResult, music.finalResult)
    private fun combine(open: String, restricted: String, musical: String): CommandHypothesis {
        val full = decode(open, average = false)
        val command = decode(restricted, average = true)
        val musicCommand = decode(musical, average = true)
        val bounds = voiceStart?.let { SpeechBounds(it, voiceEnd) }
        evaluation?.invoke(full, command, bounds)
        evaluation?.invoke(full, musicCommand, bounds)
        val result = when {
            bounds != null && ChatGptCommandGrammar.accepts(full, command, bounds) -> command
            bounds != null && SystemAssistantCommandGrammar.accepts(full, musicCommand, bounds) -> musicCommand
            bounds != null && retrySystem(full, musicCommand, bounds) -> musicCommand
            bounds != null && MusicCommandGrammar.accepts(full, musicCommand, bounds) -> musicCommand
            else -> full
        }
        replay.fill(0); replaySize = 0
        vad = AdaptiveVad(sampleRate.toInt()); preRoll.clear(); voiceStart = null; voiceEnd = 0.0
        return result
    }
    private fun retrySystem(open: CommandHypothesis, restricted: CommandHypothesis, bounds: SpeechBounds): Boolean {
        if (replaySize <= 0 || !SystemAssistantCommandGrammar.shouldRetry(open, restricted, bounds)) return false
        // Segunda lectura libre del mismo PCM, solo para la apertura ambigua del
        // asistente. No añade un capturador ni toca el texto del dictado general.
        Recognizer(model, sampleRate).use { candidate ->
            candidate.setMaxAlternatives(5)
            candidate.acceptWaveForm(ShortArray(sampleRate.toInt() / 2), sampleRate.toInt() / 2)
            candidate.acceptWaveForm(replay, replaySize)
            val alternatives = JSONObject(candidate.finalResult).optJSONArray("alternatives") ?: return false
            val texts = (0 until alternatives.length()).map { alternatives.getJSONObject(it).optString("text") }
            return SystemAssistantCommandGrammar.acceptsAlternatives(open, texts, restricted, bounds)
        }
    }
    private fun decode(json: String, average: Boolean): CommandHypothesis {
        val result = JSONObject(json)
        val words = result.optJSONArray("result")
        val confidences = if (words == null) emptyList() else (0 until words.length()).map { words.getJSONObject(it).optDouble("conf", 0.0) }
        val confidence = if (confidences.isEmpty()) 0f else if (average) confidences.average().toFloat() else confidences.min().toFloat()
        return CommandHypothesis(result.optString("text"), confidence,
            (words?.optJSONObject(0)?.optDouble("start", 0.0) ?: 0.0) + timelineOffset / sampleRate.toDouble(),
            (words?.optJSONObject(words.length() - 1)?.optDouble("end", 0.0) ?: 0.0) + timelineOffset / sampleRate.toDouble())
    }
    fun reset() {
        // Reset() nativo conserva el reloj acumulado: recrear solo decodificadores
        // al perder tramas evita registrar latencias cero con un origen nuevo.
        val nextOpen = create(false)
        val nextChat = try { create(true) } catch (problem: Throwable) { nextOpen.close(); throw problem }
        val nextMusic = try { createMusic() } catch (problem: Throwable) { nextChat.close(); nextOpen.close(); throw problem }
        try { recognizer.close() } finally { try { chat.close() } finally { music.close() } }
        recognizer = nextOpen; chat = nextChat; music = nextMusic
        samples = 0; inferenceFrames = 0; timelineOffset = 0; preRoll.clear()
        replay.fill(0); replaySize = 0
        vad = AdaptiveVad(sampleRate.toInt()); voiceStart = null; voiceEnd = 0.0
    }
    override fun close() { replay.fill(0); preRoll.clear(); try { recognizer.close() } finally { try { chat.close() } finally { try { music.close() } finally { closeModel() } } } }
}
