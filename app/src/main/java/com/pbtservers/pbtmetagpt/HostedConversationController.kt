package com.pbtservers.pbtmetagpt

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import java.io.ByteArrayOutputStream
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
    private val recognitionValue = MutableStateFlow("Dictado pendiente de captura")
    val recognition = recognitionValue.asStateFlow()
    private val timing = MutableStateFlow<Map<VoiceStage, Long>>(emptyMap())
    val metrics = timing.asStateFlow()
    private val settings = HostedSettings(context)
    private val api = GroqApi()
    private val player = PcmPlayer()
    private val android = AndroidVoiceEngine(context)
    private val kokoro = KokoroEngine(context)
    private val spain = SpainVoiceEngine(context)
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
    private val history = ConversationHistory()
    private val session = ConversationSession()
    private val sessionValue = MutableStateFlow(SessionPhase.IDLE)
    val sessionPhase = sessionValue.asStateFlow()
    private val commandValue = MutableStateFlow("")
    val commandStatus = commandValue.asStateFlow()
    private var commands: Job? = null
    private var followUp: Job? = null
    private var inactivity: Job? = null
    @Volatile private var echoText = ""
    private var following = false
    private var transitionStarted = 0L
    private val options get() = ListeningSettings(context).load()
    private fun move(phase: SessionPhase) { session.move(phase); sessionValue.value = session.phase }

    fun start(target: AssistantProfile, dictate: Boolean = false, initialQuestion: String = "") {
        stop(); chat.value = emptyList(); lastAnswer = null; resolved = null
        try {
            check(settings.provider == "groq" && settings.authorized && KeyVault(context, provider = "groq").exists())
            profile = target.validated(); fromWake = dictate
            session.start(dictate); sessionValue.value = session.phase; following = false
            mutable.value = VoiceState(active = true, connecting = dictate, sessionConfirmed = true, status = "Listo · GroqCloud", route = "Entrada pendiente de captura")
            phaseValue.value = ConversationPhase.READY
            if (!options.continuous || !dictate) limit = scope.launch { delay(target.sessionMinutes * 60000L); stop() }
            if (initialQuestion.isNotBlank()) send(initialQuestion) else if (dictate) listen()
        } catch (_: Exception) { failed(HostedFailure("Configura Groq Free y guarda un asistente antes de hablar.")) }
    }
    private fun turn(action: suspend (VoiceLatency) -> Unit) {
        if (!mutable.value.active || job?.isActive == true) return
        val current = generation
        val token = session.epoch
        idleRelease?.cancel()
        val trace = VoiceLatency()
        voiceFailure.value = null; timing.value = emptyMap()
        processing.value = true
        val next = scope.launch(start = CoroutineStart.LAZY) {
            try { awaitAudioRelease(draining); ensureActive(); action(trace) }
            catch (cancel: CancellationException) {
                if (current == generation) suspendConversation()
                throw cancel
            }
            catch (_: NoSpeechDetected) { /* El silencio de un turno no finaliza la conversación. */ }
            catch (problem: Exception) { if (current == generation) failed(problem) }
            finally {
                // El finally antiguo nunca cancela el detector de una respuesta nueva.
                if (current == generation) withContext(NonCancellable) {
                    val detector = commands; commands = null; detector?.cancel()
                    withTimeoutOrNull(5000) { detector?.join() }
                }
                if (current == generation) {
                    processing.value = false
                    trace.mark(VoiceStage.TOTAL); timing.value = trace.snapshot()
                    runCatching { LatencyStore(context).record(trace.snapshot()) }
                    mutable.update { it.copy(connecting = false, responding = false) }
                    if (voiceFailure.value == null && !mutable.value.failed) {
                        phaseValue.value = ConversationPhase.READY
                        mutable.update { it.copy(status = "Conversación activa") }
                    }
                    job = null
                    if (!mutable.value.failed && session.completed(token, options.continuous)) scheduleFollowUp()
                    else if (fromWake && !options.continuous) stop()
                    else idleRelease = scope.launch { delay(30000); android.close(); kokoro.close(); spain.close() }
                }
            }
        }
        job = next
        next.start()
    }

    fun listen() = turn { trace ->
        if (session.phase == SessionPhase.SUSPENDED) { session.resume(); sessionValue.value = session.phase }
        session.enableSpeech()
        if (options.continuous) { limit?.cancel(); limit = null }
        move(if (following) SessionPhase.FOLLOW_UP_LISTENING else SessionPhase.USER_LISTENING)
        phaseValue.value = ConversationPhase.LISTENING
        mutable.update { it.copy(connecting = true, failed = false, paused = false, status = "Preparando escucha") }
        ContinuousWakeService.beforeRealtime()
        val started = trace.now()
        val speechEnd = java.util.concurrent.atomic.AtomicLong(0)
        val text = HostedDictation(context).capture(profile?.language ?: "es-ES", continuous = session.spoken && options.continuous, ready = {
            trace.mark(VoiceStage.MICROPHONE_READY)
            mutable.update { it.copy(status = "Te escucho", connecting = false) }
            inactivity?.cancel()
            if (options.inactivitySeconds > 0) inactivity = scope.launch { delay(options.inactivitySeconds * 1000L); stop() }
            if (transitionStarted > 0) {
                LatencyStore(context).record(mapOf(VoiceStage.FOLLOW_UP_READY to (SystemClock.elapsedRealtime() - transitionStarted)))
                transitionStarted = 0
            }
        }, speechStarted = { trace.mark(VoiceStage.SPEECH_START); inactivity?.cancel(); inactivity = null }, speechEnded = {
            if (speechEnd.compareAndSet(0, trace.now())) trace.once(VoiceStage.END_OF_SPEECH, trace.now() - started)
        }, engine = { selected ->
            (context.applicationContext as PbtApplication).voiceTransitions.engine(selected)
            recognitionValue.value = when (selected) {
            RecognitionEngine.VOSK -> "Vosk español · local"
            RecognitionEngine.ANDROID_LOCAL -> "Android local · ${profile?.language ?: "es-ES"}"
            RecognitionEngine.GROQ_WHISPER -> "Groq Whisper · español · audio enviado con autorización"
        } })
        inactivity?.cancel(); inactivity = null
        val captureEnd = speechEnd.get().takeIf { it > 0 } ?: started
        trace.add(VoiceStage.TRANSCRIPTION, trace.now() - captureEnd)
        generate(text, trace)
    }
    fun send(text: String) = turn { generate(text, it) }
    /** La fotografía solo llega aquí después de confirmar su envío en la interfaz. */
    fun analyzePhoto(bitmap: Bitmap) = turn { trace ->
        check(settings.authorized && settings.provider == "groq")
        if (bitmap.isRecycled) throw HostedFailure("La fotografía ya no está disponible.")
        val image = withContext(Dispatchers.Default) {
            val size = maxOf(bitmap.width, bitmap.height)
            val scaled = if (size > 1280) Bitmap.createScaledBitmap(bitmap,
                maxOf(1, bitmap.width * 1280 / size), maxOf(1, bitmap.height * 1280 / size), true) else bitmap
            try {
                ByteArrayOutputStream().use { stream ->
                    check(scaled.compress(Bitmap.CompressFormat.JPEG, 78, stream))
                    stream.toByteArray()
                }
            } finally { if (scaled !== bitmap) scaled.recycle() }
        }
        phaseValue.value = ConversationPhase.THINKING
        mutable.update { it.copy(status = "Analizando foto con Groq Vision", connecting = true) }
        val question = "Describe lo que se ve en esta fotografía en español de España. Señala cualquier incertidumbre."
        val started = trace.now()
        val answer = api.analyzeImage(KeyVault(context, provider = "groq").read(), image, question)
        trace.add(VoiceStage.GROQ_COMPLETE, trace.now() - started)
        chat.update { (it + ChatMessage(++messageId, false, "¿Qué hay en la fotografía?")
            + ChatMessage(++messageId, true, answer)).takeLast(20) }
        mutable.update { it.copy(connecting = false, transcript = answer, sessionConfirmed = true) }
        val selected = requireNotNull(profile)
        lastAnswer = selected to answer
        speakStream(selected, trace, produce = { delta -> delta(answer); answer }, text = {})
    }

    private suspend fun generate(text: String, trace: VoiceLatency) {
        WebCommands.parse(text)?.let { (context.applicationContext as PbtApplication).openWeb(it); return }
        val local = VoiceCommands.parse(text, profile?.name.orEmpty(), options)
        if (local != null) { handle(local); return }
        trace.responseStarted()
        require(text.trim().length in 1..2000 && !SecretPatterns.contains(text))
        check(settings.authorized && settings.provider == "groq")
        val target = requireNotNull(profile)
        val prompt = target.prompt()
        if (prompt.length > 8000) throw HostedFailure("Reduce las instrucciones del asistente a menos de 8000 caracteres para este modo gratuito.")
        history.user(text.trim())
        val conversation = history.snapshot()
        val instruction = prompt + if (target.ttsEngine == "qwen_space" && target.voiceMode != "rapida") "\nResponde con menos de 700 caracteres para voz." else ""
        val messages = listOf("system" to instruction) + conversation
        chat.update { (it + ChatMessage(++messageId, false, text.trim()) + ChatMessage(++messageId, true, "")).takeLast(20) }
        val replyId = messageId
        phaseValue.value = ConversationPhase.THINKING
        move(SessionPhase.PROCESSING)
        mutable.update { it.copy(connecting = true, failed = false, status = "Pensando") }
        ContinuousWakeService.beforeRealtime()
        echoText = ""
        val token = session.epoch
        prepareOutputRoute()
        startCommands(token)
        val full = StringBuilder()
        val result = speakStream(target, trace, produce = { delta ->
            val groqStart = trace.now()
            val response = api.answerStream(KeyVault(context, provider = "groq").read(), settings.model, messages) { part ->
                trace.once(VoiceStage.GROQ_FIRST_TOKEN, trace.now() - groqStart)
                delta(part)
            }
            trace.add(VoiceStage.GROQ_COMPLETE, trace.now() - groqStart)
            currentCoroutineContext().ensureActive()
            check(session.accepts(token))
            mutable.update { it.copy(connecting = false, sessionConfirmed = true) }
            response
        }, text = { part ->
            check(session.accepts(token))
            full.append(part)
            echoText = full.toString()
            chat.update { list -> list.map { if (it.id == replyId) it.copy(text = full.toString()) else it } }
            mutable.update { it.copy(transcript = "Tú: $text\n\nGroqCloud: $full") }
        })
        currentCoroutineContext().ensureActive()
        if (!session.accepts(token)) return
        // Confirmar el asistente solo tras terminar también su reproducción.
        history.assistant(result)
        lastAnswer = target to result
    }
    private suspend fun resolve(target: AssistantProfile): AssistantProfile {
        if (target.voiceMode != "rapida") return target
        resolved?.let { return it }
        val selected = android.resolveRapid(target)
        resolved = selected
        return selected
    }
    private fun prepareOutputRoute() {
        if (route != null) return
        val bluetooth = context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false)
        val selected = AudioRoute(context) { suspendConversation() }
        try {
            val label = selected.start(bluetooth, speaker = !bluetooth)
            route = selected
            mutable.update { it.copy(route = label) }
        } catch (problem: Exception) { selected.close(); throw problem }
    }
    private suspend fun speakStream(target: AssistantProfile, trace: VoiceLatency,
        produce: suspend (suspend (String) -> Unit) -> String, text: (String) -> Unit,
    ): String {
        val rvcStore = RvcOnnxStore(context)
        var localRvc: RvcOnDevice? = null
        try {
            // RVC local tiene coste fijo alto por inferencia HuBERT/RMVPE/ONNX.
            // Agrupar texto en frases mayores evita cargar la CPU con fragmentos
            // cortos; el texto sigue mostrándose por streaming independientemente.
            val costlyRvc = rvcStore.enabled && (target.voiceMode == "rapida" || target.ttsEngine in setOf("kokoro", "piper_es", "android"))
            return StreamSpeechPipeline().run(remote = target.voiceMode != "rapida" && target.ttsEngine == "qwen_space",
                groupedLocal = costlyRvc,
                produce = produce, text = text,
        synthesize = { phrase ->
            trace.mark(VoiceStage.FIRST_SENTENCE)
            val started = trace.now()
            val chosen = resolve(target)
            engineValue.value = VoiceSynthesisContract.label(chosen.ttsEngine) + " · " + chosen.ttsVoice
            try {
                // Abrir la ruta de salida ANTES de sintetizar: la negociación Bluetooth/
                // Meta deja de competir con la primera sílaba del PCM generado.
                prepareOutputRoute()
                val normalAudio = when (chosen.ttsEngine) {
                    "kokoro" -> kokoro.synthesize(chosen, phrase) { trace.mark(VoiceStage.TTS_FIRST_PCM) }
                    "piper_es" -> spain.synthesize(chosen, phrase).also { trace.mark(VoiceStage.TTS_FIRST_PCM) }
                    "android" -> android.synthesize(chosen, phrase)
                    "qwen_space" -> {
                        if (!settings.qwenAuthorized) throw HostedFailure("Autoriza Qwen3-TTS en Voces antes de enviarle texto.")
                        HostedHttp.qwenApi.synthesize(chosen, phrase, { message -> mutable.update { it.copy(status = message) } }, trace)
                    }
                    else -> throw HostedFailure("Esta voz no está habilitada en Groq Free. Elige Rápida o Natural; no se ha llamado a una API de pago.")
                }
                if (chosen.ttsEngine in setOf("android", "kokoro", "piper_es") && rvcStore.enabled) {
                    mutable.update { it.copy(status = "Convirtiendo voz RVC en el teléfono") }
                    val rvcStart = trace.now()
                    try {
                        val converter = localRvc ?: RvcOnDevice(rvcStore).also { localRvc = it }
                        val output = ByteArrayOutputStream()
                        var rate = 0
                        for (segment in RvcAudioSegments.split(normalAudio)) {
                            currentCoroutineContext().ensureActive()
                            val converted = converter.convert(segment)
                            check(rate == 0 || rate == converted.sampleRate)
                            rate = converted.sampleRate
                            require(output.size() + converted.bytes.size <= 6_000_000) { "Respuesta RVC demasiado larga." }
                            output.write(converted.bytes)
                        }
                        PcmAudio(output.toByteArray(), rate)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (_: Exception) {
                        throw HostedFailure("RVC local no pudo convertir la voz. Verifica los tres ONNX compatibles o desactiva RVC en Voces.")
                    } finally {
                        trace.add(VoiceStage.RVC_CONVERSION, trace.now() - rvcStart)
                    }
                } else normalAudio
            } finally { trace.add(VoiceStage.TTS_SYNTHESIS, trace.now() - started) }
        }, play = { audio ->
            try {
                val bluetooth = context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false)
                player.playStream(flow {
                    for (chunk in audio) {
                        if (route == null) throw HostedFailure("No se ha preparado la salida de audio.")
                        mutable.update { it.copy(responding = true, status = "Hablando") }
                        phaseValue.value = ConversationPhase.SPEAKING
                        move(SessionPhase.SPEAKING)
                        emit(chunk)
                    }
                }, firstFrame = { trace.mark(VoiceStage.FIRST_AUDIO_FRAME); timing.value = trace.snapshot() },
                    preRollMs = if (bluetooth) 250 else 0)
            } finally { route?.close(); route = null }
        }, failed = { problem ->
            voiceFailure.value = publicError(problem)
            phaseValue.value = ConversationPhase.ERROR
            mutable.update { it.copy(status = "La voz no está disponible. Puedes leer la respuesta.") }
        })
        } finally {
            localRvc?.close()
        }
    }

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
        session.suspendSession(); sessionValue.value = session.phase
        phaseValue.value = ConversationPhase.ERROR
        mutable.update { it.copy(status = publicError(problem), failed = true) }
    }
    fun stop() {
        session.stop(); sessionValue.value = session.phase
        generation++; processing.value = false
        followUp?.cancel(); followUp = null; inactivity?.cancel(); inactivity = null
        commands?.cancel()
        val old = job ?: draining
        job?.cancel(); job = null
        val detector = commands; commands = null
        limit?.cancel(); idleRelease?.cancel(); player.stop(); route?.close(); route = null; android.close()
        draining = scope.launch { old?.join(); detector?.join() }
        idleRelease = scope.launch { delay(30000); if (!mutable.value.active) { kokoro.close(); spain.close() } }
        history.clear(); profile = null; resolved = null
        phaseValue.value = ConversationPhase.READY
        mutable.value = VoiceState(status = "Listo", transcript = mutable.value.transcript)
    }
    private fun scheduleFollowUp() {
        followUp?.cancel()
        following = true
        transitionStarted = SystemClock.elapsedRealtime()
        sessionValue.value = session.phase
        followUp = scope.launch { yield(); if (mutable.value.active && session.phase != SessionPhase.SUSPENDED) listen() }
    }
    private fun startCommands(token: Long) {
        commands?.cancel()
        if (!session.spoken || !options.interruption) return
        commands = scope.launch {
            LocalCommandListener(context).run(profile?.name.orEmpty(), options,
                speaking = { sessionValue.value == SessionPhase.SPEAKING }, echoText = { echoText },
                status = { if (session.accepts(token)) commandValue.value = it }, unavailable = {
                    if (session.accepts(token)) suspendConversation()
                }, command = { command, detection ->
                    if (session.accepts(token)) {
                        LatencyStore(context).record(mapOf(VoiceStage.STOP_DETECTION to detection))
                        handle(command)
                    }
                })
        }
    }
    private fun handle(command: LocalVoiceCommand) {
        when (command) {
            LocalVoiceCommand.INTERRUPT -> interrupt()
            LocalVoiceCommand.END -> stop()
            LocalVoiceCommand.STOP_LISTENING -> {
                ListeningSettings(context).save(options.copy(background = false))
                ContinuousWakeService.stop(context); stop()
                (context.applicationContext as PbtApplication).stopVisibleWake?.invoke()
            }
            LocalVoiceCommand.CHATGPT -> (context.applicationContext as PbtApplication).openChatGpt()
            LocalVoiceCommand.SYSTEM_ASSISTANT -> (context.applicationContext as PbtApplication).openSystemAssistant()
            LocalVoiceCommand.GOOGLE -> (context.applicationContext as PbtApplication).openWeb(WebAction(WebDestination.GOOGLE))
            LocalVoiceCommand.BROWSER -> (context.applicationContext as PbtApplication).openWeb(WebAction(WebDestination.BROWSER))
            LocalVoiceCommand.MUSIC_PLAY, LocalVoiceCommand.MUSIC_PAUSE, LocalVoiceCommand.MUSIC_NEXT, LocalVoiceCommand.MUSIC_PREVIOUS ->
                (context.applicationContext as PbtApplication).controlSpotify(requireNotNull(MusicCommands.action(command)))
        }
    }
    /** Prioridad de parada: invalidar antes de detener PCM o esperar trabajo nativo. */
    fun interrupt() {
        if (!mutable.value.active) return
        val started = SystemClock.elapsedRealtime()
        session.interrupt(); sessionValue.value = session.phase
        generation++; followUp?.cancel(); inactivity?.cancel(); inactivity = null
        commands?.cancel()
        val previous = job ?: draining; job?.cancel(); job = null; draining = previous
        player.stop(); android.close()
        // Una respuesta parcial sigue visible, pero no se envía como respuesta completa en el contexto.
        chat.update { list -> list.map { if (it == list.lastOrNull() && it.assistant) it.copy(text = it.text + "\n[Interrumpida]") else it } }
        mutable.update { it.copy(responding = false, connecting = false, status = "Parando") }
        phaseValue.value = ConversationPhase.READY
        processing.value = false
        LatencyStore(context).record(mapOf(VoiceStage.STOP_AUDIO_REQUEST to (SystemClock.elapsedRealtime() - started)))
        followUp = scope.launch {
            try { awaitAudioRelease(previous) }
            catch (_: HostedFailure) { failed(HostedFailure("El audio interrumpido sigue ocupado. Detén y reanuda la conversación.")); return@launch }
            if (mutable.value.active && session.phase == SessionPhase.INTERRUPTING) {
                if (session.spoken) { move(SessionPhase.FOLLOW_UP_LISTENING); scheduleFollowUp() }
                else { move(SessionPhase.IDLE); mutable.update { it.copy(status = "Conversación activa") } }
            }
        }
    }
    fun suspendConversation() {
        if (!mutable.value.active) return
        generation++; session.suspendSession(); sessionValue.value = session.phase
        followUp?.cancel(); inactivity?.cancel(); inactivity = null; commands?.cancel()
        job?.let { it.cancel(); draining = it }; job = null
        player.stop(); android.close(); route?.close(); route = null
        processing.value = false
        mutable.update { it.copy(paused = true, responding = false, connecting = false, status = "Pausado · pulsa Micrófono para reanudar") }
    }
    suspend fun awaitReleased() {
        withTimeout(9000) {
            do { val pending = draining; awaitAudioRelease(pending) } while (pending !== draining)
            if (!mutable.value.active) awaitAudioRelease(commands)
        }
    }
    fun clear() { stop(); chat.value = emptyList(); lastAnswer = null; timing.value = emptyMap(); voiceFailure.value = null; mutable.value = VoiceState(status = "Listo") }
}
