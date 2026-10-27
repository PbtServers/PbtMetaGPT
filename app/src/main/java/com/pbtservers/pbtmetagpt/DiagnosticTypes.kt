package com.pbtservers.pbtmetagpt

enum class Check(val title: String) {
    PERMISSIONS("Permisos Android"), BLUETOOTH("Bluetooth Android"), ROUTE("Ruta de comunicación"),
    DAT_INIT("Inicialización DAT"), DAT_REGISTRATION("Registro en Meta AI"), DAT_DEVICES("Dispositivos DAT"),
    DAT_SESSION("Sesión con las gafas"), CAMERA_PERMISSION("Permiso de cámara en Meta AI"),
    CAMERA_STREAM("Flujo de cámara DAT"), CAMERA_CAPTURE("Captura de foto DAT"),
    BACKEND("HTTPS del backend opcional"), CREDENTIAL("Credencial temporal de OpenAI"), SDP("Negociación SDP"),
    ICE("Transporte WebRTC / ICE"), DATA_CHANNEL("Canal de eventos Realtime"), REALTIME("Confirmación de sesión OpenAI"),
    MICROPHONE("Captura de micrófono"), PLAYBACK("Reproducción de audio"), LOCAL_MIC("Prueba local de micrófono"),
    LOCAL_SPEAKER("Prueba local de altavoz"), CONTROL("Control de sesión"), PROVIDER("Errores de OpenAI"),
    TTS_ENGINE("Motor de síntesis y PCM"), WAKE("Reconocimiento local de frases"),
}
enum class CheckStatus(val label: String) { PENDING("Sin comprobar"), RUNNING("En curso"), OK("Observado"), WARNING("Requiere revisión"), ERROR("Error") }
data class DiagnosticEntry(val check: Check, val status: CheckStatus = CheckStatus.PENDING, val detail: String = "No se ha realizado esta comprobación.", val code: String = "", val time: Long = 0, val manual: Boolean = false)
data class DiagnosticState(val entries: Map<Check, DiagnosticEntry> = Check.entries.associateWith { DiagnosticEntry(it) }, val history: List<DiagnosticEntry> = emptyList())
