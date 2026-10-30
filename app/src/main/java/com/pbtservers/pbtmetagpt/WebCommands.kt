package com.pbtservers.pbtmetagpt

enum class WebDestination { GOOGLE, BROWSER, SEARCH }
data class WebAction(val destination: WebDestination, val query: String = "")

/** Enunciados imperativos completos. La consulta conserva tildes y nombres propios. */
object WebCommands {
    fun parse(text: String): WebAction? {
        val normalized = VoiceCommands.normalize(text)
        if (normalized in setOf("google", "abre google", "abre la aplicacion google")) return WebAction(WebDestination.GOOGLE)
        if (normalized in setOf("abre el navegador", "abre navegador")) return WebAction(WebDestination.BROWSER)
        val match = Regex("^\\s*busca\\s+en\\s+google(?:\\s+(.+))?\\s*[.!]?\\s*$", RegexOption.IGNORE_CASE).matchEntire(text.trim()) ?: return null
        val query = match.groupValues[1].trim().trimEnd('.', '!').trim()
        if (query.length > 500 || SecretPatterns.contains(query)) return null
        return WebAction(WebDestination.SEARCH, query)
    }
}
