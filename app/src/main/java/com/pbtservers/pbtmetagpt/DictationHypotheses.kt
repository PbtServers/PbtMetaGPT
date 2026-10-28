package com.pbtservers.pbtmetagpt

object DictationHypotheses {
    fun best(texts: List<String>, confidence: FloatArray?): String {
        val candidates = texts.take(5).withIndex().filter { it.value.isNotBlank() }
        if (confidence == null || confidence.size != texts.size) return candidates.firstOrNull()?.value?.trim().orEmpty()
        return candidates.maxByOrNull { confidence[it.index].takeIf { score -> score.isFinite() && score in 0f..1f } ?: -1f }
            ?.value?.trim().orEmpty()
    }
}
