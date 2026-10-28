package com.pbtservers.pbtmetagpt

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel

/** Callback de audio sin inferencia, esperas ni modificaciones del buffer WebRTC. */
class CommandPcmQueue {
    class Frame internal constructor() {
        val samples = ShortArray(4096)
        var size = 0
        var rate = 0
        var receivedMs = 0L
        var serial = 0L
    }
    private val free = ArrayBlockingQueue<Frame>(12).apply { repeat(12) { add(Frame()) } }
    private val closed = AtomicBoolean(false)
    private val sequence = AtomicLong(0)
    val discontinuity = AtomicBoolean(false)
    val frames = Channel<Frame>(8)
    fun submit(buffer: ByteBuffer, format: Int, channels: Int, rate: Int, bytes: Int, receivedMs: Long) {
        if (closed.get()) return
        val serial = sequence.incrementAndGet()
        if (format != 2 || channels !in 1..2 || rate !in 8000..48000 || bytes <= 0 || bytes > buffer.limit() || bytes % (channels * 2) != 0) return
        val size = bytes / (channels * 2)
        if (size > 4096) return
        val frame = free.poll() ?: run { discontinuity.set(true); return }
        frame.size = size; frame.rate = rate; frame.receivedMs = receivedMs
        frame.serial = serial
        repeat(size) { index ->
            var sum = 0
            repeat(channels) { channel ->
                val offset = (index * channels + channel) * 2
                sum += ((buffer.get(offset).toInt() and 255) or (buffer.get(offset + 1).toInt() shl 8)).toShort().toInt()
            }
            frame.samples[index] = (sum / channels).toShort()
        }
        if (!frames.trySend(frame).isSuccess) { discontinuity.set(true); release(frame) }
    }
    fun release(frame: Frame) { frame.samples.fill(0); frame.size = 0; free.offer(frame) }
    fun close() {
        closed.set(true); frames.close()
        while (true) release(frames.tryReceive().getOrNull() ?: break)
    }
}
