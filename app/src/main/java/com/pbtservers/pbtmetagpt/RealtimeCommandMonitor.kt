package com.pbtservers.pbtmetagpt

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.*
import org.vosk.LibVosk
import org.vosk.LogLevel

/** Decodifica una copia acotada del capturador WebRTC; no posee AudioRecord. */
class RealtimeCommandMonitor(private val context: Context, private val scope: CoroutineScope) {
    val input = CommandPcmQueue()
    private var work: Job? = null
    fun start(name: String, options: ListeningOptions, allowed: () -> Boolean, echoSafe: (String) -> Boolean,
        status: (String) -> Unit, command: (LocalVoiceCommand, Long) -> Unit) {
        work = scope.launch(Dispatchers.IO) {
            var decoder: LocalCommandDecoder? = null
            var rate = 0
            var started = 0L
            var previousSerial = 0L
            var protectedUntil = SystemClock.elapsedRealtime() + 350
            val gate = InterruptionGate()
            try {
                LibVosk.setLogLevel(LogLevel.WARNINGS)
                for (frame in input.frames) try {
                    if (!allowed()) continue
                    if (decoder == null || rate != frame.rate) {
                        decoder?.close(); rate = frame.rate
                        decoder = LocalCommandDecoder(WakeModel(context).model.absolutePath, rate.toFloat())
                        started = frame.receivedMs - frame.size * 1000L / rate
                        withContext(Dispatchers.Main) { status("Interrupción por voz experimental · sin control de eco activo usa Parar") }
                    }
                    if (input.discontinuity.getAndSet(false) || (previousSerial != 0L && frame.serial != previousSerial + 1)) {
                        decoder.reset(); started = frame.receivedMs - frame.size * 1000L / rate
                        protectedUntil = SystemClock.elapsedRealtime() + 350
                    }
                    previousSerial = frame.serial
                    val hypothesis = decoder.accept(frame.samples, frame.size) ?: continue
                    val now = SystemClock.elapsedRealtime()
                    val accepted = gate.accept(hypothesis.text, hypothesis.confidence, now, protectedUntil,
                        echoSafe(hypothesis.text), name, options)
                    if (accepted != null) withContext(Dispatchers.Main) {
                        command(accepted, (now - started - (hypothesis.onsetSeconds * 1000).toLong()).coerceAtLeast(0))
                    }
                } finally { input.release(frame) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { withContext(Dispatchers.Main) { status("Órdenes locales no disponibles · utiliza Parar") } }
            catch (_: LinkageError) { withContext(Dispatchers.Main) { status("Motor de órdenes no disponible · utiliza Parar") } }
            finally { decoder?.close(); input.close() }
        }
    }
    fun stop() { input.close(); work?.cancel(); work = null }
}
