package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class Check(val title: String) {
    PERMISSIONS("Permisos Android"), BLUETOOTH("Bluetooth Android"), ROUTE("Ruta de comunicación"),
    DAT_INIT("Inicialización DAT"), DAT_REGISTRATION("Registro en Meta AI"), DAT_DEVICES("Dispositivos DAT"),
    DAT_SESSION("Sesión con las gafas"), CAMERA_PERMISSION("Permiso de cámara en Meta AI"),
    CAMERA_STREAM("Flujo de cámara DAT"), CAMERA_CAPTURE("Captura de foto DAT"),
    BACKEND("HTTPS del backend opcional"), CREDENTIAL("Credencial temporal de OpenAI"), SDP("Negociación SDP"),
    ICE("Transporte WebRTC / ICE"), DATA_CHANNEL("Canal de eventos Realtime"), REALTIME("Confirmación de sesión OpenAI"),
    MICROPHONE("Captura de micrófono"), PLAYBACK("Reproducción de audio"), LOCAL_MIC("Prueba local de micrófono"),
    LOCAL_SPEAKER("Prueba local de altavoz"), CONTROL("Control de sesión"), PROVIDER("Errores de OpenAI"),
}
enum class CheckStatus(val label: String) { PENDING("Sin comprobar"), RUNNING("En curso"), OK("Observado"), WARNING("Requiere revisión"), ERROR("Error") }
data class DiagnosticEntry(val check: Check, val status: CheckStatus = CheckStatus.PENDING, val detail: String = "No se ha realizado esta comprobación.", val code: String = "", val time: Long = 0, val manual: Boolean = false)
data class DiagnosticState(val entries: Map<Check, DiagnosticEntry> = Check.entries.associateWith { DiagnosticEntry(it) }, val history: List<DiagnosticEntry> = emptyList())

/** Solo admite textos definidos por la aplicación. Nunca pasar mensajes/cuerpos externos. */
class Diagnostics(private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(DiagnosticState())
    val state = mutable.asStateFlow()
    fun record(check: Check, status: CheckStatus, detail: String, code: String = "", manual: Boolean = false) {
        val safeCode = safeCode(code)
        val entry = DiagnosticEntry(check, status, detail, safeCode, now(), manual)
        mutable.update { it.copy(entries = it.entries + (check to entry), history = (it.history + entry).takeLast(60)) }
    }
    fun reset() { mutable.value = DiagnosticState() }
    fun report(): String = buildString {
        appendLine("PbtMetaGPT 0.4.0 · informe de diagnóstico")
        appendLine("Los estados observados no certifican compatibilidad física. Hora en milisegundos UTC.")
        state.value.entries.values.forEach { appendLine("${it.check.title}: ${it.status.label} | ${it.detail} | ${it.code} | ${it.time} | ${if (it.manual) "confirmación manual" else "observación del sistema"}") }
        appendLine("Últimos eventos:")
        state.value.history.forEach { appendLine("${it.time} ${it.check.title} ${it.status.label} ${it.code}") }
    }
    companion object {
        fun safeCode(value: String): String = value.takeIf {
            it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && !Regex("sk[-_]|ek_", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }.orEmpty()
        fun sdkCode(error: Any): String = safeCode(if (error is Enum<*>) error.name else error.javaClass.simpleName)
    }
}
