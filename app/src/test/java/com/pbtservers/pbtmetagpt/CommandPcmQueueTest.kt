package com.pbtservers.pbtmetagpt

import java.nio.ByteBuffer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CommandPcmQueueTest {
    @Test fun capturaCopiaMonoSinCambiarElBufferDelProveedor() = runBlocking<Unit> {
        val queue = CommandPcmQueue()
        val bytes = byteArrayOf(0, 0x40, 0, 0xC0.toByte())
        val buffer = ByteBuffer.wrap(bytes).apply { position(3) }
        queue.submit(buffer, 2, 1, 16000, 4, 123)
        val frame = queue.frames.receive()
        assertEquals(3, buffer.position()); assertArrayEquals(bytes, buffer.array())
        assertArrayEquals(shortArrayOf(16384, -16384), frame.samples.copyOf(frame.size))
        assertEquals(123, frame.receivedMs)
        queue.release(frame); assertTrue(frame.samples.all { it == 0.toShort() }); queue.close()
    }
    @Test fun mezclaStereoYRechazaFormatosNoComprobados() = runBlocking<Unit> {
        val queue = CommandPcmQueue()
        val buffer = ByteBuffer.wrap(byteArrayOf(0, 0x40, 0, 0xC0.toByte()))
        queue.submit(buffer, 4, 2, 48000, 4, 0)
        assertTrue(queue.frames.tryReceive().isFailure)
        queue.submit(buffer, 2, 2, 48000, 4, 0)
        val frame = queue.frames.receive()
        assertEquals(1, frame.size); assertEquals(0.toShort(), frame.samples[0])
        queue.release(frame); queue.close()
    }
    @Test fun sobrecargaAcotadaSenalaDiscontinuidadYElCierreBorraDatos() {
        val queue = CommandPcmQueue()
        repeat(1000) { queue.submit(ByteBuffer.wrap(byteArrayOf(1, 2)), 2, 1, 16000, 2, it.toLong()) }
        assertTrue(queue.discontinuity.get())
        queue.close()
        assertTrue(queue.frames.tryReceive().isFailure)
        queue.submit(ByteBuffer.wrap(byteArrayOf(1, 2)), 2, 1, 16000, 2, 1001)
        assertTrue(queue.frames.tryReceive().isFailure)
    }
}
