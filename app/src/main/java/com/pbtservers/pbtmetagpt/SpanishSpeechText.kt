package com.pbtservers.pbtmetagpt

/** Pronunciación española de nombres/siglas antes de eSpeak; conserva texto de interfaz y negaciones. */
object SpanishSpeechText {
    fun pronunciation(text: String): String = text
        .replace(Regex("\\bchatgpt\\b", RegexOption.IGNORE_CASE), "chat ge pe te")
        .replace(Regex("\\bgpt\\b", RegexOption.IGNORE_CASE), "ge pe te")
        .replace(Regex("\\bgoogle\\b", RegexOption.IGNORE_CASE), "gúguel")
}
