package com.pbtservers.pbtmetagpt

data class RecognitionErrors(val reference: Int, val substitutions: Int, val deletions: Int, val insertions: Int) {
    val rate: Double? get() = if (reference == 0) null else (substitutions + deletions + insertions).toDouble() / reference
    operator fun plus(other: RecognitionErrors) = RecognitionErrors(reference + other.reference, substitutions + other.substitutions, deletions + other.deletions, insertions + other.insertions)
}

/** Alineación reproducible: minúsculas, sin puntuación ni tildes; no detecta idioma por sí sola. */
object RecognitionMetrics {
    fun words(reference: String, hypothesis: String): RecognitionErrors = compare(tokens(reference), tokens(hypothesis))
    fun characters(reference: String, hypothesis: String): RecognitionErrors = compare(VoiceCommands.normalize(reference).toList(), VoiceCommands.normalize(hypothesis).toList())
    private fun tokens(text: String) = VoiceCommands.normalize(text).split(' ').filter { it.isNotBlank() }
    private fun <T> compare(reference: List<T>, hypothesis: List<T>): RecognitionErrors {
        data class Cost(val distance: Int, val substitutions: Int = 0, val deletions: Int = 0, val insertions: Int = 0)
        var previous = Array(hypothesis.size + 1) { Cost(it, insertions = it) }
        for (row in reference.indices) {
            val current = Array(hypothesis.size + 1) { Cost(0) }
            current[0] = Cost(row + 1, deletions = row + 1)
            for (column in hypothesis.indices) {
                val same = reference[row] == hypothesis[column]
                val diagonal = previous[column].let { it.copy(distance = it.distance + if (same) 0 else 1, substitutions = it.substitutions + if (same) 0 else 1) }
                val deletion = previous[column + 1].let { it.copy(distance = it.distance + 1, deletions = it.deletions + 1) }
                val insertion = current[column].let { it.copy(distance = it.distance + 1, insertions = it.insertions + 1) }
                current[column + 1] = listOf(diagonal, deletion, insertion).minBy { it.distance }
            }
            previous = current
        }
        val total = previous.last()
        return RecognitionErrors(reference.size, total.substitutions, total.deletions, total.insertions)
    }
}
