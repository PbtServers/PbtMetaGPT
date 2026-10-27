package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class ConversationPhase(val label: String) { READY("Listo"), LISTENING("Escuchando"), THINKING("Pensando"), SPEAKING("Hablando"), ERROR("Error") }
data class ChatMessage(val id: Long, val assistant: Boolean, val text: String)

/** Contexto privado solo en memoria. Ningún fallback facturable, una única generación cancelable. */
class HostedConversation(private val context: Context, private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(VoiceState(status = "Listo"))
    val state = mutable.asStateFlow()
    private val processing = MutableStateFlow(false)
    val busy = processing.asStateFlow()
    private val phaseValue = MutableStateFlow(ConversationPhase.READY)
    val phase = phaseValue.asStateFlow()
    private val chat = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages = chat.asStateFlow()
    private val voiceFailure = MutableStateFlow<String?>(null)
    val audioError = voiceFailure.asStateFlow()
    private val engineValue = MutableStateFlow("Voz pendiente de selección")
    val engine = engineValue.asStateFlow()
    private val timing = MutableStateFlow<Map<VoiceStage, Long>>(emptyMap())
    val metrics = timing.asStateFlow()
    private val settings = HostedSettings(context)
    private val api = GroqApi()
    private val player = PcmPlayer()
    private val android = AndroidVoiceEngine(context)
    private var job: Job? = null
    private var draining: Job? = null
    private var limit: Job? = null
    private var idleRelease: Job? = null
    private var generation = 0L
    private var messageId = 0L
    private var profile: AssistantProfile? = null
    private var resolved: AssistantProfile? = null
    private var lastAnswer: Pair<AssistantProfile, String>? = null
    private var route: AudioRoute? = null
    private var fromWake = false
    private val history = mutableListOf<Pair<String, String>>()

    fun start(target: AssistantProfile, dictate: Boolean = false) {
        stop(); chat.value = emptyList(); lastAnswer = null; resolved = null
        try {
            check(settings.provider == "groq" && settings.authorized && KeyVault(context, provider = "groq").exists())
            profile = target.validated(); fromWake = dictate
            mutable.value = VoiceState(active = true, connecting = dictate, status = "Listo · GroqCloud", route = "Micrófono local del teléfono")
            phaseValue.value = ConversationPhase.READY
            limit = scope.launch { delay(target.sessionMinutes * 60000L); stop() }
            if (dictate) listen()
        } catch (_: Exception) { failed(HostedFailure("Configura Groq Free y guarda un asistente antes de hablar.")) }
    }
    private fun turn(action: suspend (VoiceLatency) -> Unit) {
        if (!mutable.value.active || job?.isActive == true) return
        val current = generation
        idleRelease?.cancel()
        val trace = VoiceLatency()
        voiceFailure.value = null; timing.value = emptyMap()
        processing.value = true
        job = scope.launch {
            try { draining?.join(); ensureActive(); action(trace) }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { failed(problem) }
            finally {
                if (current == generation) {
                    processing.value = false
                    trace.mark(VoiceStage.TOTAL); timing.value = trace.snapshot()
                    runCatching { LatencyStore(context).record(trace.snapshot()) }
                    mutable.update { it.copy(connecting = false, responding = false, active = if (fromWake && !it.failed) false else it.active) }
                    if (voiceFailure.value == null && !mutable.value.failed) {
                        phaseValue.value = ConversationPhase.READY
                        mutable.update { it.copy(status = "Listo", active = if (fromWake) false else it.active) }
                    }
                    idleRelease = scope.launch { delay(30000); android.close() }
                }
            }
        }
    }
    fun listen() = turn { trace ->
        if (profile?.language?.startsWith("es") != true) throw HostedFailure("El dictado Vosk instalado es español. Para otros idiomas escribe tu petición.")
        phaseValue.value = ConversationPhase.LISTENING
        mutable.update { it.copy(connecting = true, failed = false, status = "Escuchando") }
        ContinuousWakeService.beforeRealtime()
        val started = trace.now()
        val text = HostedDictation(context).capture()
        trace.add(VoiceStage.TRANSCRIPTION, trace.now() - started)
        generate(text, trace)
    }
    fun send(text: String) = turn { generate(text, it) }

    private suspend fun generate(text: String, trace: VoiceLatency) {
        trace.responseStarted()
        require(text.trim().length in 1..2000 && !SecretPatterns.contains(text))
        check(settings.authorized && settings.provider == "groq")
        val target = requireNotNull(profile)
        val prompt = target.prompt()
        if (prompt.length > 8000) throw HostedFailure("Reduce las instrucciones del asistente a menos de 8000 caracteres para este modo gratuito.")
        var conversation = (history + ("user" to text.trim())).takeLast(9).dropWhile { it.first != "user" }
        while (conversation.sumOf { it.second.length } > 15000 && conversation.size > 1) conversation = conversation.drop(2)
        val instruction = prompt + if (target.ttsEngine == "qwen_space" && target.voiceMode != "rapida") "\nResponde con menos de 700 caracteres para voz." else ""
        val messages = listOf("system" to instruction) + conversation
        chat.update { (it + ChatMessage(++messageId, false, text.trim()) + ChatMessage(++messageId, true, "")).takeLast(20) }
        val replyId = messageId
        phaseValue.value = ConversationPhase.THINKING
        mutable.update { it.copy(connecting = true, failed = false, status = "Pensando") }
        ContinuousWakeService.beforeRealtime()
        val full = StringBuilder()
        val result = speakStream(target, trace, produce = { delta ->
            val groqStart = trace.now()
            val response = api.answerStream(KeyVault(context, provider = "groq").read(), settings.model, messages) { part ->
                trace.once(VoiceStage.GROQ_FIRST_TOKEN, trace.now() - groqStart)
                delta(part)
            }
            trace.add(VoiceStage.GROQ_COMPLETE, trace.now() - groqStart)
            history.clear(); history.addAll(conversation + ("assistant" to response))
            lastAnswer = target to response
            mutable.update { it.copy(connecting = false, sessionConfirmed = true) }
            response
        }, text = { part ->
            full.append(part)
            chat.update { list -> list.map { if (it.id == replyId) it.copy(text = full.toString()) else it } }
            mutable.update { it.copy(transcript = "Tú: $text\n\nGroqCloud: $full") }
        })
        lastAnswer = target to result
    }
    private suspend fun resolve(target: AssistantProfile): AssistantProfile {
        if (target.voiceMode != "rapida") return target
        resolved?.let { return it }
        val selected = android.resolveRapid(target)
        resolved = selected
        return selected
    }
    private suspend fun speakStream(target: AssistantProfile, trace: VoiceLatency,
        produce: suspend (suspend (String) -> Unit) -> String, text: (String) -> Unit,
    ): String = StreamSpeechPipeline().run(remote = target.voiceMode != "rapida" && target.ttsEngine == "qwen_space", produce = produce, text = text,
        synthesize = { phrase ->
            val started = trace.now()
            val chosen = resolve(target)
            engineValue.value = VoiceSynthesisContract.label(chosen.ttsEngine) + " · " + chosen.ttsVoice
            try {
                when (chosen.ttsEngine) {
                    "android" -> android.synthesize(chosen, phrase)
                    "qwen_space" -> {
                        if (!settings.qwenAuthorized) throw HostedFailure("Autoriza Qwen3-TTS en Voces antes de enviarle texto.")
                        HostedHttp.qwenApi.synthesize(chosen, phrase, { message -> mutable.update { it.copy(status = message) } }, trace)
                    }
                    else -> throw HostedFailure("Esta voz no está habilitada en Groq Free. Elige Rápida o Natural; no se ha llamado a una API de pago.")
                }
            } finally { trace.add(VoiceStage.TTS_SYNTHESIS, trace.now() - started) }
        }, play = { audio ->
            try {
                player.playStream(flow {
                    for (chunk in audio) {
                        if (route == null) {
                            route = AudioRoute(context) { stop() }
                            val bluetooth = context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false)
                            val label = route!!.start(bluetooth)
                            mutable.update { it.copy(route = label, responding = true, status = "Hablando") }
                        }
                        phaseValue.value = ConversationPhase.SPEAKING
                        emit(chunk)
                    }
                }) { trace.mark(VoiceStage.FIRST_AUDIO_FRAME); timing.value = trace.snapshot() }
            } finally { route?.close(); route = null }
        }, failed = { problem ->
            voiceFailure.value = publicError(problem)
            phaseValue.value = ConversationPhase.ERROR
            mutable.update { it.copy(status = "La voz no está disponible. Puedes leer la respuesta.") }
        })

    fun replay(voice: AssistantProfile? = null) {
        val previous = lastAnswer ?: return
        if (job?.isActive == true) return
        profile = previous.first
        if (limit?.isActive != true) limit = scope.launch { delay(previous.first.sessionMinutes * 60000L); stop() }
        fromWake = false; resolved = null
        mutable.update { it.copy(active = true, failed = false) }
        turn { trace ->
            ContinuousWakeService.beforeRealtime()
            speakStream(voice ?: previous.first, trace, { delta -> delta(previous.second); previous.second }, {})
        }
    }
    private fun publicError(problem: Exception) = if (problem is HostedFailure) problem.publicMessage else "No se pudo completar la operación. Revisa red, voz, permisos y cuota."
    private fun failed(problem: Exception) {
        phaseValue.value = ConversationPhase.ERROR
        mutable.update { it.copy(status = publicError(problem), failed = true) }
    }
    fun stop() {
        generation++; processing.value = false
        job?.let { it.cancel(); draining = it }; job = null
        limit?.cancel(); idleRelease?.cancel(); player.stop(); route?.close(); route = null; android.close()
        history.clear(); profile = null; resolved = null
        phaseValue.value = ConversationPhase.READY
        mutable.value = VoiceState(status = "Listo", transcript = mutable.value.transcript)
    }
    fun clear() { stop(); chat.value = emptyList(); lastAnswer = null; timing.value = emptyMap(); voiceFailure.value = null; mutable.value = VoiceState(status = "Listo") }
}
