package com.pbtservers.pbtmetagpt
/** Sustituciones internas de pruebas: sin componentes Android exportados ni lectura de archivos externos. */
internal object DevelopmentVoice {
    var wakeFrames: ((ShortArray) -> Int)? = null
    var confirmation: (suspend () -> Unit)? = null
    var capture: (suspend (VoiceCaptureRequest) -> String)? = null
    var api: GroqApi? = null
    var synthesis: (suspend (AssistantProfile, String) -> PcmAudio)? = null
    fun reset() { wakeFrames = null; confirmation = null; capture = null; api = null; synthesis = null }
}
