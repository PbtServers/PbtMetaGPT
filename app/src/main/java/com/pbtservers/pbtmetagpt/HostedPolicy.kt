package com.pbtservers.pbtmetagpt

import java.io.IOException

/** Errores públicos, nunca el cuerpo HTTP (puede contener texto o credenciales). */
class HostedFailure(val publicMessage: String) : IOException(publicMessage)

object HostedPolicy {
    fun failure(code: Int, retryAfter: String? = null): HostedFailure = HostedFailure(when (code) {
        401, 403 -> "Credencial o acceso rechazado. Revisa la cuenta y sus permisos."
        404, 410 -> "Modelo o servicio no disponible. Selecciona otro modelo en Servicios de IA."
        429 -> "Cuota gratuita agotada o límite temporal. " + (retryAfter?.toLongOrNull()?.coerceIn(1, 86400)?.let { "Espera $it segundos." } ?: "Consulta los límites de tu cuenta.")
        503, 504 -> "Servicio ocupado o no disponible. Vuelve a intentarlo más tarde."
        else -> "El proveedor rechazó la solicitud (HTTP $code)."
    })
    fun freeVoice(engine: String) = engine in setOf("android", "qwen_space", "kokoro", "piper_es")
    fun model(value: String): String {
        require(value.matches(Regex("[A-Za-z0-9][A-Za-z0-9./_-]{0,119}")) && !SecretPatterns.contains(value)) { "Modelo no válido." }
        return value
    }
}
