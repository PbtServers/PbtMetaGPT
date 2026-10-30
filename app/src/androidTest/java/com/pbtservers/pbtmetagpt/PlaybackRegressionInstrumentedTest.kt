package com.pbtservers.pbtmetagpt
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

/** AudioTrack sustituido: no acredita Bluetooth físico. */
class PlaybackRegressionInstrumentedTest {
    private class Output(private val writable: Boolean, private val advancing: Boolean) : PcmOutput {
        @Volatile var closed = false
        @Volatile var frames = 0L
        override fun play() = Unit
        override fun head() = if (advancing) frames else 0L
        override fun write(bytes: ByteArray, offset: Int, count: Int): Int {
            if (!writable) return 0
            frames += count / 2; return count
        }
        override fun close() { closed = true }
    }
    @Test fun contadorCongeladoOEscrituraSinProgresoSeOmitenYLiberanAntesDeContinuar() = runBlocking {
        for ((writable, advancing) in listOf(true to true, true to false, false to false)) {
            val output = Output(writable, advancing)
            val result = OptionalConfirmation.run(true, 200) { PcmPlayer { output }.play(WakeConfirmation.pcm()) }
            assertEquals(if (advancing) ConfirmationResult.PLAYED else ConfirmationResult.OMITTED, result)
            assertTrue(output.closed)
            val next = Output(true, true)
            assertEquals(ConfirmationResult.PLAYED, OptionalConfirmation.run(true) { PcmPlayer { next }.play(WakeConfirmation.pcm()) })
            assertTrue(next.closed)
        }
    }
    @Test fun cancelarDuranteEscrituraDetieneSalidaYNoEntregaTramasPosteriores() = runBlocking {
        val opened = CompletableDeferred<Unit>(); val output = Output(false, false)
        var continued = false
        val player = PcmPlayer { opened.complete(Unit); output }
        val job = launch { OptionalConfirmation.run(true, 10000) { player.play(WakeConfirmation.pcm()) }; continued = true }
        opened.await(); job.cancelAndJoin()
        assertTrue(output.closed); assertFalse(continued)
    }
}
