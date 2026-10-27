package com.pbtservers.pbtmetagpt

import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/** Contrato BYOK aislado de Android. No incluye URL configurables ni registra cabeceras/cuerpos. */
object DirectCredentials {
    fun request(key: String, profile: AssistantProfile?): Request {
        require(key.matches(Regex("sk-[A-Za-z0-9_-]{20,500}"))) { "La clave personal no tiene un formato válido. Sustitúyela en OpenAI." }
        val body = profile?.let(RealtimeSettings::secretRequest) ?: JSONObject()
            .put("expires_after", JSONObject().put("anchor", "created_at").put("seconds", 60))
            .put("session", JSONObject().put("type", "realtime").put("model", RealtimeSettings.model)).toString()
        return Request.Builder().url("https://api.openai.com/v1/realtime/client_secrets")
            .header("Authorization", "Bearer $key").post(body.toRequestBody("application/json".toMediaType())).build()
    }
    internal fun decode(reply: HttpReply, nowSeconds: Long): String {
        if (!reply.isSuccessful) throw DiagnosticFailure("OPENAI_HTTP_${reply.code}", when (reply.code) {
            401 -> "OpenAI rechazó la clave API. Sustitúyela en Configuración de OpenAI."
            429 -> "OpenAI ha rechazado la petición por cuota o límite de uso. Revisa la facturación de la API."
            else -> "OpenAI rechazó la credencial temporal (HTTP ${reply.code}). Revisa acceso a gpt-realtime y conexión."
        })
        return RealtimeProtocol.secret(reply.body, nowSeconds)
    }
}
