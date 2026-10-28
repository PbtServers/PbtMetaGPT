package com.pbtservers.pbtmetagpt

/** Presupuesto estricto para una futura conversión RVC totalmente local.
 * No implica que haya un runtime RVC compatible instalado.
 */
object RvcLocalBudget {
    const val MAX_MODEL_BYTES = 192L * 1024 * 1024
    const val MAX_INDEX_BYTES = 64L * 1024 * 1024
    const val MAX_PCM_BYTES = 2 * 1024 * 1024
    const val MAX_FRAME_MS = 160

    fun validModelSize(modelBytes: Long, indexBytes: Long): Boolean =
        modelBytes in 128..MAX_MODEL_BYTES && indexBytes in 0..MAX_INDEX_BYTES

    /** PCM16 mono y frames cortos: evita acumular conversaciones en el heap. */
    fun validFrame(byteCount: Int, sampleRate: Int, channels: Int, durationMs: Int): Boolean {
        if (sampleRate !in 8000..48000 || channels != 1 || durationMs !in 1..MAX_FRAME_MS) return false
        val expected = sampleRate.toLong() * 2L * durationMs / 1000L
        return byteCount > 0 && byteCount % 2 == 0 && byteCount <= MAX_PCM_BYTES &&
            byteCount.toLong() <= expected + 2
    }

    /** El modo sin índice evita RAM/búsquedas kNN cuando no hay benchmark. */
    fun useIndex(availableRamMb: Long, lowMemory: Boolean, explicitlyEnabled: Boolean): Boolean =
        explicitlyEnabled && !lowMemory && availableRamMb >= 2048
}
