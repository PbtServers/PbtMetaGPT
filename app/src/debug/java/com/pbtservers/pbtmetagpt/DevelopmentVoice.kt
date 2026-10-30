package com.pbtservers.pbtmetagpt
/** Sustituciones internas de pruebas: sin componentes Android exportados ni lectura de archivos externos. */
internal object DevelopmentVoice {
    @Volatile var wakeFrames: ((ShortArray) -> Int)? = null
    @Volatile var confirmation: (suspend () -> Unit)? = null
    @Volatile var capture: (suspend (VoiceCaptureRequest) -> String)? = null
    @Volatile var api: GroqApi? = null
    @Volatile var synthesis: (suspend (AssistantProfile, String) -> PcmAudio)? = null
    fun reset() { wakeFrames = null; confirmation = null; capture = null; api = null; synthesis = null }
}
