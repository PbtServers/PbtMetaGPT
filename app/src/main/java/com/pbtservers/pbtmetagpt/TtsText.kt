package com.pbtservers.pbtmetagpt

/** Texto hablado separado del chat. No inventa SSML ni parámetros emocionales. */
object TtsText {
    fun clean(text: String): String {
        val stream = SpeechMarkup()
        return (stream.append(text) + stream.finish()).trim()
    }
}

/** Conserva el estado entre tokens de red, antes de buscar fronteras de frases. */
class SpeechMarkup {
    private val pending = StringBuilder()
    private var lineStart = true
    private var fenced = false
    private var previousRaw: Char? = null
    fun append(delta: String): String { pending.append(delta); return drain(false) }
    fun finish(): String = drain(true)

    private fun drain(final: Boolean): String {
        val out = StringBuilder()
        var i = 0
        fun literal(value: String): String = value.replace("*", " asterisco ")
        while (i < pending.length) {
            val c = pending[i]
            val remaining = pending.substring(i)
            val previous = if (i > 0) pending[i - 1] else previousRaw
            if (!final && c in "*_~" && i + 1 == pending.length) break
            if (!final && (remaining == "\\" || remaining.all { it == '`' } && remaining.length < 3)) break
            if (remaining.startsWith("```")) {
                if (fenced) { fenced = false; i += 3; continue }
                val newline = pending.indexOf("\n", i + 3)
                if (newline < 0 && !final) break
                fenced = true; i = if (newline < 0) pending.length else newline + 1
                lineStart = true; continue
            }
            if (fenced) {
                out.append(if (c == '*') " asterisco " else c.toString())
                lineStart = c == '\n'; i++; continue
            }
            if (c == '\\' && i + 1 < pending.length && pending[i + 1] in "*_`[]#~\\") {
                out.append(literal(pending[i + 1].toString())); i += 2; lineStart = false; continue
            }
            if (lineStart && c in "#>-+* ") {
                val prefix = Regex("^(?: {0,3}#{1,6} | {0,3}> ?| {0,3}[-+*] )").find(remaining)
                if (prefix != null) { i += prefix.value.length; continue }
                if (!final && remaining.all { it in "#>-+* " }) break
            }
            if (c == '`') {
                val close = pending.indexOf("`", i + 1)
                if (close < 0 && !final) break
                if (close >= 0) { out.append(literal(pending.substring(i + 1, close))); i = close + 1; lineStart = false; continue }
                i++; continue
            }
            val image = c == '!' && i + 1 < pending.length && pending[i + 1] == '['
            if (c == '[' || image) {
                val begin = i + if (image) 2 else 1
                val close = pending.indexOf("]", begin)
                if ((close < 0 || close + 1 >= pending.length) && !final) break
                if (close >= 0 && close + 1 < pending.length && pending[close + 1] == '(') {
                    val end = pending.indexOf(")", close + 2)
                    if (end < 0 && !final) break
                    if (end >= 0) { out.append(TtsText.clean(pending.substring(begin, close))); i = end + 1; lineStart = false; continue }
                }
            }
            if (c in "*_~" && previous?.isLetterOrDigit() != true) {
                var count = 1
                while (i + count < pending.length && pending[i + count] == c && count < 3) count++
                if (i + count >= pending.length && !final) break
                val marker = c.toString().repeat(count)
                if (i + count < pending.length && !pending[i + count].isWhitespace()) {
                    var close = pending.indexOf(marker, i + count)
                    while (close >= 0 && (close == i + count || pending[close - 1].isWhitespace())) close = pending.indexOf(marker, close + count)
                    if (close < 0 && !final) break
                    if (close < 0 && final) { i += count; continue }
                    if (close >= 0) { out.append(TtsText.clean(pending.substring(i + count, close))); i = close + count; lineStart = false; continue }
                }
            }
            out.append(if (c == '*') {
                if (previous?.isDigit() == true && i + 1 < pending.length && pending[i + 1].isDigit()) " por " else " asterisco "
            } else c.toString())
            lineStart = c == '\n'; i++
        }
        if (i > 0) previousRaw = pending[i - 1]
        pending.delete(0, i)
        return out.toString()
    }
}
