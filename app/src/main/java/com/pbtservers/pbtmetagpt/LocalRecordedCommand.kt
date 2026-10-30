package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.*

/** Resolver órdenes breves antes de subir el dictado a Whisper. No abre otro micrófono. */
class LocalRecordedCommand(private val context: Context) {
    suspend fun recognize(wav: ByteArray, name: String, options: ListeningOptions): String? = withContext(Dispatchers.IO) {
        // Solo WAV propios de hasta seis segundos. Las preguntas largas no ejecutan otro ASR.
        if (wav.size !in 46..192044 || !WakeModel(context).installed()) return@withContext null
        val buffer = ShortArray(320)
        val hypotheses = mutableListOf<CommandHypothesis>()
        try {
            val app = context.applicationContext as PbtApplication
            app.voskModels.acquire(WakeModel(context).model.absolutePath).use { lease ->
                LocalCommandDecoder(WakeModel(context).model.absolutePath, sharedModel = lease.model).use { decoder ->
                    var position = 44
                    while (position + 1 < wav.size) {
                        ensureActive()
                        val count = minOf(buffer.size, (wav.size - position) / 2)
                        repeat(count) { index -> buffer[index] = ((wav[position + index * 2].toInt() and 255) or (wav[position + index * 2 + 1].toInt() shl 8)).toShort() }
                        decoder.accept(buffer, count)?.takeIf { it.text.isNotBlank() }?.let(hypotheses::add)
                        buffer.fill(0); position += count * 2
                    }
                    decoder.finish().takeIf { it.text.isNotBlank() }?.let(hypotheses::add)
                }
            }
            // No ejecutar un fragmento si el resto de la intervención contiene otra cosa.
            val text = hypotheses.joinToString(" ") { it.text }
            val confidence = hypotheses.minOfOrNull { it.confidence } ?: return@withContext null
            val known = VoiceCommands.parse(text, name, options) != null || WebCommands.parse(text, name) != null
            if (known && (!confidence.isFinite() || confidence < .9f)) throw RepeatLocalCommand()
            text.takeIf { known }
        } catch (cancel: CancellationException) { throw cancel }
        catch (repeat: RepeatLocalCommand) { throw repeat }
        catch (_: Exception) { null } // Solo continúa el Whisper ya autorizado, nunca un proveedor nuevo.
        catch (_: LinkageError) { null }
        finally { buffer.fill(0); hypotheses.clear() }
    }
}
