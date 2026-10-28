package com.pbtservers.pbtmetagpt

object VoiceModes {
    fun qwen(profile: AssistantProfile, speaker: String): AssistantProfile {
        require(speaker in QwenSpaceContract.speakers)
        return profile.copy(ttsEngine = "qwen_space", voiceMode = "natural", ttsVoice = speaker,
            ttsPackage = profile.ttsPackage.takeIf { it in QwenSpaceContract.sizes } ?: "0.6B")
    }
    fun forEngine(engine: String) = when (engine) { "android" -> "rapida"; "qwen_space" -> "natural"; else -> "personalizada" }
    fun label(mode: String) = when (mode) { "rapida" -> "Rápida"; "natural" -> "Natural"; else -> "Personalizada" }
    fun choose(profile: AssistantProfile, mode: String): AssistantProfile = when (mode) {
        "rapida" -> if (profile.ttsEngine == "android") profile.copy(voiceMode = mode)
            else VoiceSelection.engine(profile, "realtime").copy(voiceMode = mode) // marcador automático; ambos controladores resuelven Android antes de audio/red
        "natural" -> if (profile.ttsEngine == "android" && profile.ttsVoice.isNotBlank() && profile.ttsPackage.isNotBlank())
            profile.copy(voiceMode = mode) else qwen(profile,
                profile.ttsVoice.takeIf { it in QwenSpaceContract.speakers } ?: "Ryan")
        "personalizada" -> profile.copy(voiceMode = mode)
        else -> throw IllegalArgumentException("Modo de voz no compatible.")
    }
}
