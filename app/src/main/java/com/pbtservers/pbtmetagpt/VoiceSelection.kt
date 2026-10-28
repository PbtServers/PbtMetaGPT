package com.pbtservers.pbtmetagpt

/** Selección pura: nunca abre audio, red, navegación ni almacenamiento. */
object VoiceSelection {
    val engines = setOf("realtime", "android", "openai_tts", "external", "sherpa_vits", "qwen_space", "kokoro", "piper_es")
    fun catalog(engine: String) = if (engine == "openai_tts") RealtimeSettings.speechVoices else RealtimeSettings.voices
    fun engine(profile: AssistantProfile, engine: String): AssistantProfile {
        require(engine in engines) { "Motor de voz no compatible." }
        if (engine == "kokoro") return VoiceModes.kokoro(profile)
        if (engine == "piper_es") return VoiceModes.spain(profile)
        if (engine == profile.ttsEngine) return profile
        return profile.copy(ttsEngine = engine, ttsVoice = "", ttsPackage = "", pitch = 1.0, voiceMode = VoiceModes.forEngine(engine),
            voice = profile.voice.takeIf { it in catalog(engine) } ?: "marin")
    }
    fun official(profile: AssistantProfile, voice: String): AssistantProfile {
        require(profile.ttsEngine in setOf("realtime", "openai_tts") && voice in catalog(profile.ttsEngine)) {
            "Esta voz no está disponible en el motor seleccionado."
        }
        return profile.copy(voice = voice)
    }
}
