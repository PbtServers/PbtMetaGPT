package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*

/** Coordinación real compartida por todos los motores: generación, cancelación y exclusión de audio. */
internal class SpeechTaskQueue(private val scope: CoroutineScope,
    private val render: suspend (AssistantProfile, String) -> PcmAudio,
    private val play: suspend (PcmAudio) -> Unit,
    private val stopPlayback: () -> Unit,
    private val status: (String) -> Unit,
    private val error: (String) -> Unit,
) {
    private var job: Job? = null
    private var generation = 0
    fun speak(profile: AssistantProfile, text: String, finished: () -> Unit = {}) {
        stop()
        val current = generation
        job = scope.launch {
            try {
                status("Generando voz alternativa · micrófono apagado")
                val audio = render(profile, text)
                ensureActive()
                if (current != generation) return@launch
                status("Reproduciendo voz alternativa · micrófono apagado")
                play(audio)
            } catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) { if (current == generation) error(if (problem is DiagnosticFailure) problem.message.orEmpty() else "No se pudo sintetizar o reproducir con el motor elegido. Revisa su configuración, modelo, diccionario, permisos y cuota. No se ha cambiado de voz automáticamente.") }
            finally { if (current == generation) { job = null; finished() } }
        }
    }
    fun stop() { generation++; job?.cancel(); job = null; stopPlayback() }
}
