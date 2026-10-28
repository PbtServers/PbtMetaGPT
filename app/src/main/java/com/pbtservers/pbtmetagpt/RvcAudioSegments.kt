package com.pbtservers.pbtmetagpt

/** Partición PCM sin perder muestras; deja margen al límite nativo de ocho segundos. */
object RvcAudioSegments {
    fun split(audio: PcmAudio): List<PcmAudio> {
        require(audio.sampleRate in 8000..48000 && audio.bytes.size >= 3200 && audio.bytes.size % 2 == 0)
        val limit = audio.sampleRate * 2 * 7
        val result = mutableListOf<PcmAudio>(); var offset = 0
        while (offset < audio.bytes.size) {
            val remaining = audio.bytes.size - offset
            val length = when {
                remaining <= limit -> remaining
                remaining - limit < 3200 -> (remaining / 4) * 2
                else -> limit
            }
            result += PcmAudio(audio.bytes.copyOfRange(offset, offset + length), audio.sampleRate)
            offset += length
        }
        return result
    }
}
