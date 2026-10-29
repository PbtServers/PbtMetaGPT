package com.pbtservers.pbtmetagpt

/** Deletreo español explícito antes de eSpeak: evita leer GPT como una palabra concatenada. */
object SpanishSpeechText {
    fun pronunciation(text: String): String = text
        .replace(Regex("\\bchatgpt\\b", RegexOption.IGNORE_CASE), "chat ge pe te")
        .replace(Regex("\\bgpt\\b", RegexOption.IGNORE_CASE), "ge pe te")
}
