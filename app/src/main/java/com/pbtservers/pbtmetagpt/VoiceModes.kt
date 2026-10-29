package com.pbtservers.pbtmetagpt

object VoiceModes {
    fun spain(profile: AssistantProfile, voice: String = "davefx") = profile.copy(voiceMode = "natural", ttsEngine = "piper_es",
        ttsVoice = voice, ttsPackage = SpainVoiceCatalog.voice(voice).packageId, language = "es-ES", pitch = 1.0)
    fun kokoro(profile: AssistantProfile, voice: String = "ef_dora"): AssistantProfile {
        require(voice in setOf("ef_dora", "em_alex", "em_santa"))
        return profile.copy(voiceMode = "personalizada", ttsEngine = "kokoro", ttsVoice = voice, ttsPackage = "kokoro-v1.0", pitch = 1.0)
    }
    fun qwen(profile: AssistantProfile, speaker: String): AssistantProfile {
        require(speaker in QwenSpaceContract.speakers)
        return profile.copy(ttsEngine = "qwen_space", voiceMode = "personalizada", ttsVoice = speaker,
            ttsPackage = profile.ttsPackage.takeIf { it in QwenSpaceContract.sizes } ?: "0.6B")
    }
    fun forEngine(engine: String) = when (engine) { "android" -> "rapida"; "piper_es" -> "natural"; else -> "personalizada" }
    fun label(mode: String) = when (mode) { "rapida" -> "Rápida"; "natural" -> "Natural"; else -> "Avanzada" }
    fun choose(profile: AssistantProfile, mode: String): AssistantProfile = when (mode) {
        "rapida" -> if (profile.ttsEngine == "android") profile.copy(voiceMode = mode)
            else VoiceSelection.engine(profile, "realtime").copy(voiceMode = mode) // marcador automático; ambos controladores resuelven Android antes de audio/red
        "natural" -> if (profile.ttsEngine == "piper_es") profile.copy(voiceMode = mode) else spain(profile)
        "personalizada" -> profile.copy(voiceMode = mode)
        else -> throw IllegalArgumentException("Modo de voz no compatible.")
    }
}
