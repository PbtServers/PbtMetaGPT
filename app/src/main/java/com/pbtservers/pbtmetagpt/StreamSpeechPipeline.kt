package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel

/** Una síntesis anticipada y una reproducción. Fallar la voz no bloquea el texto. */
class StreamSpeechPipeline {
    suspend fun run(remote: Boolean, produce: suspend (suspend (String) -> Unit) -> String,
        text: (String) -> Unit, synthesize: suspend (String) -> PcmAudio,
        play: suspend (ReceiveChannel<PcmAudio>) -> Unit, failed: (Exception) -> Unit,
        groupedLocal: Boolean = false,
    ): String = coroutineScope {
        val segments = SpeechSegments(remote, groupedLocal)
        val phrases = Channel<String>(192) // respuesta <=4000 caracteres: nunca retiene el streaming por esperar TTS
        val audio = Channel<PcmAudio>(1)
        var voiceFailed = false
        val synthesis = launch {
            try {
                for (phrase in phrases) {
                    ensureActive()
                    if (!voiceFailed) try {
                        val prepared = synthesize(phrase)
                        ensureActive()
                        audio.send(prepared)
                    }
                    catch (cancel: CancellationException) { throw cancel }
                    catch (problem: Exception) { ensureActive(); voiceFailed = true; failed(problem) }
                }
            } finally { audio.close() }
        }
        val playback = launch {
            try { play(audio) }
            catch (cancel: CancellationException) { throw cancel }
            catch (problem: Exception) {
                ensureActive()
                voiceFailed = true; failed(problem)
                for (ignored in audio) { ensureActive() } // vaciar sin bloquear al productor de texto
            }
        }
        try {
            var characters = 0
            val result = produce { delta ->
                ensureActive()
                characters += delta.length
                require(characters <= 4000)
                text(delta)
                segments.append(delta).forEach { phrases.send(it) }
            }
            segments.finish()?.let { phrases.send(it) }
            phrases.close()
            synthesis.join(); playback.join()
            result
        } finally { phrases.cancel(); audio.cancel() }
    }
}
