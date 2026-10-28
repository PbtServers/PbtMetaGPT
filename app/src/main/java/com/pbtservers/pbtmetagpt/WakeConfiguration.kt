package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale
import java.util.UUID

data class ActivationPhrase(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "Atento",
    val appendName: Boolean = true,
    val enabled: Boolean = true,
    val primary: Boolean = false,
) {
    fun rendered(name: String) = (text.trim() + if (appendName) " ${name.trim()}" else "").trim()
    fun validate(name: String) {
        require(id.length in 1..80 && rendered(name).length in 2..160 && PhraseMatcher.normalize(rendered(name)).isNotBlank()) { "Cada frase debe tener entre 2 y 160 caracteres reconocibles." }
        require(!SecretPatterns.contains(text)) { "No incluyas secretos en las frases." }
    }
    fun json() = JSONObject().put("id", id).put("text", text).put("appendName", appendName).put("enabled", enabled).put("primary", primary)
    companion object {
        fun parse(json: JSONObject): ActivationPhrase {
            require(json.keys().asSequence().all { it in setOf("id", "text", "appendName", "enabled", "primary") }) { "Campos de frase no permitidos." }
            return ActivationPhrase(json.getString("id"), json.getString("text"), json.getBoolean("appendName"), json.getBoolean("enabled"), json.getBoolean("primary"))
        }
    }
}



object PhraseMatcher {
    fun normalize(text: String): String = Normalizer.normalize(text.lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "").replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")
    fun exact(heard: String, target: String) = normalize(target).isNotBlank() && normalize(heard) == normalize(target)
    fun matches(heard: String, target: String, tolerance: Boolean): Boolean {
        if (exact(heard, target)) return true
        if (!tolerance) return false
        val a = normalize(heard).split(" "); val b = normalize(target).split(" ")
        // Solo una diferencia en la última palabra larga. No tolerar el prefijo ni añadir peticiones.
        if (a.size != b.size || b.last().length < 5 || a.dropLast(1) != b.dropLast(1)) return false
        val x = a.last(); val y = b.last()
        if (kotlin.math.abs(x.length - y.length) > 1) return false
        var previous = IntArray(y.length + 1) { it }
        x.forEachIndexed { i, c ->
            val row = IntArray(y.length + 1); row[0] = i + 1
            y.forEachIndexed { j, d -> row[j + 1] = minOf(row[j] + 1, previous[j + 1] + 1, previous[j] + if (c == d) 0 else 1) }
            previous = row
        }
        return previous.last() == 1
    }
    /** Una coincidencia ambigua entre personajes se rechaza, nunca gana por orden de la lista. */
    fun resolve(heard: String, profiles: List<AssistantProfile>): AssistantProfile? {
        fun find(fuzzy: Boolean) = profiles.filter { p -> p.activationPhrases.any { phrase -> phrase.enabled &&
            if (fuzzy) matches(heard, phrase.rendered(p.name), p.wakeSensitivity != "baja" && (p.tolerantActivation || p.wakeSensitivity == "alta")) else exact(heard, phrase.rendered(p.name)) } }
        val exact = find(false)
        if (exact.isNotEmpty()) return exact.singleOrNull()
        return find(true).singleOrNull()
    }
}

/** Entrada de resultados finales con generación: cancela callbacks viejos y consume una sola activación. */
data class WakeUtterance(val profile: AssistantProfile, val question: String = "")

class ActivationGate {
    private var generation = 0
    private var profiles = emptyList<AssistantProfile>()
    var armed = false
        private set
    fun arm(candidates: List<AssistantProfile>): Int {
        candidates.forEach { it.validated() }
        profiles = candidates.toList(); armed = true
        return ++generation
    }
    fun accept(heard: String, epoch: Int): AssistantProfile? {
        return accept(listOf(heard), epoch)
    }
    fun accept(hypotheses: List<String>, epoch: Int): AssistantProfile? {
        if (!armed || epoch != generation) return null
        // Una alternativa no puede seleccionar un personaje distinto de otra.
        // Baja: primera hipótesis exacta; normal: tres; alta: cinco y tolerancia limitada.
        val matches = profiles.filter { profile ->
            val count = when (profile.wakeSensitivity) { "baja" -> 1; "alta" -> 5; else -> 3 }
            hypotheses.take(count).any { heard -> PhraseMatcher.resolve(heard, listOf(profile)) != null }
        }
        val match = matches.singleOrNull() ?: return null
        stop(); return match
    }
    /** Conserva la pregunta si llega en la misma frase final; nunca busca una activación dentro de una conversación. */
    fun acceptUtterance(hypotheses: List<String>, epoch: Int): WakeUtterance? {
        if (!armed || epoch != generation) return null
        val matches = profiles.mapNotNull { profile ->
            val count = when (profile.wakeSensitivity) { "baja" -> 1; "alta" -> 5; else -> 3 }
            hypotheses.take(count).firstNotNullOfOrNull { heard ->
                if (PhraseMatcher.resolve(heard, listOf(profile)) != null) WakeUtterance(profile)
                else profile.activationPhrases.filter { it.enabled }.firstNotNullOfOrNull { phrase ->
                    val words = Regex("[\\p{L}\\p{N}]+").findAll(heard).toList()
                    val targetWords = PhraseMatcher.normalize(phrase.rendered(profile.name)).split(' ').size
                    if (targetWords < 2 || words.size <= targetWords) null
                    else {
                        val end = words[targetWords - 1].range.last + 1
                        if (PhraseMatcher.exact(heard.substring(0, end), phrase.rendered(profile.name)))
                            WakeUtterance(profile, heard.substring(end).trim().trimStart(',', '.', ':', ';', '!', '?').trim().take(2000)) else null
                    }
                }
            }
        }
        val match = matches.singleOrNull() ?: return null
        stop(); return match
    }
    fun stop() { generation++; armed = false; profiles = emptyList() }
}
