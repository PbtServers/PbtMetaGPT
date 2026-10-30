package com.pbtservers.pbtmetagpt
/** Release no ofrece instaladores ni estado mutable de inyección. */
internal object DevelopmentVoice {
    val wakeFrames: ((ShortArray) -> Int)? get() = null
    val confirmation: (suspend () -> Unit)? get() = null
    val capture: (suspend (VoiceCaptureRequest) -> String)? get() = null
    val api: GroqApi? get() = null
    val synthesis: (suspend (AssistantProfile, String) -> PcmAudio)? get() = null
}
