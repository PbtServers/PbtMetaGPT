package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Esperar una liberación nunca autoriza abrir un segundo micrófono si vence el plazo. */
suspend fun awaitAudioRelease(job: Job?, timeoutMs: Long = 8000) {
    if (job == null) return
    if (withTimeoutOrNull(timeoutMs) { job.join(); true } != true)
        throw HostedFailure("El micrófono anterior no se ha liberado. Detén la escucha y vuelve a activarla.")
}

/** La síntesis y el capturador de órdenes tienen ciclos de cancelación distintos. */
fun drainAudioJobs(scope: CoroutineScope, previous: Job?, detector: Job?): Job = scope.launch {
    previous?.join()
    detector?.join()
}

enum class TransitionReason { START, READY, DETECTED, HANDOFF, RETURN, PAUSE, STOP, TIMEOUT, FAILURE, OPTIONAL_AUDIO_SKIPPED }

/** Solo categorías y números. No admite textos de usuario, excepciones ni identificadores externos. */
class VoiceTransitions(private val clock: () -> Long) {
    private var session = 0L
    private var previous = ListeningPhase.OFF
    private var at = clock()
    private val events = ArrayDeque<String>()
    @Synchronized fun record(next: ListeningPhase, reason: TransitionReason, owner: MicrophoneOwner?, input: Int) {
        val now = clock()
        if (reason == TransitionReason.START) session++
        events.addLast("sesión=$session $previous→$next etapa_ms=${(now - at).coerceAtLeast(0)} motivo=$reason mic=${owner ?: "LIBRE"} entrada_tipo=$input")
        while (events.size > 64) events.removeFirst()
        previous = next; at = now
    }
    @Synchronized fun report(): String = events.joinToString("\n")
    @Synchronized fun engine(value: RecognitionEngine) {
        events.addLast("sesión=$session motor=$value")
        while (events.size > 64) events.removeFirst()
    }
}
