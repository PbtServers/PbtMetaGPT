package com.pbtservers.pbtmetagpt

object KokoroText {
    /** eSpeak devuelve fonemas por cláusula; reponer puntuación conserva señales de prosodia para Kokoro. */
    fun phonemes(text: String, convert: (String) -> String): String {
        return raw(text, convert).replace("tʃ", "ʧ")
    }
    fun raw(text: String, convert: (String) -> String): String {
        val result = StringBuilder(); var offset = 0
        for (boundary in Regex("[.!?;:,](?!\\d)").findAll(text)) {
            val end = boundary.range.last + 1
            val part = text.substring(offset, end)
            result.append(convert(part).trim()).append(boundary.value).append(' ')
            offset = end
        }
        if (offset < text.length) result.append(convert(text.substring(offset)).trim())
        return result.toString().replace("g", "ɡ")
            .replace(Regex("\\([^)]*\\)"), "").replace("\u0361", "").replace("\u032f", "").trim()
    }
}
