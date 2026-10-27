package com.pbtservers.pbtmetagpt

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object RealtimeProtocol {
    fun backendFailure(body: String, status: Int): DiagnosticFailure {
        val code = runCatching { JSONObject(body).optString("code") }.getOrDefault("")
        val description = when (code) {
            "OPENAI_AUTH" -> "OpenAI rechazó la clave del servidor. Revisa la configuración del backend."
            "OPENAI_ACCESS" -> "El proyecto de OpenAI no tiene acceso a la operación solicitada."
            "OPENAI_MODEL" -> "El modelo configurado no está disponible en OpenAI."
            "OPENAI_QUOTA" -> "OpenAI ha limitado la solicitud. Revisa facturación, cuota y límites."
            "OPENAI_TIMEOUT", "OPENAI_NETWORK" -> "El backend no pudo contactar con OpenAI a tiempo."
            "OPENAI_INVALID_SECRET" -> "El proveedor devolvió una credencial temporal inválida."
            "BACKEND_AUTH" -> "El backend ha rechazado tu token personal."
            "BACKEND_RATE_LIMIT" -> "El backend ha limitado las sesiones. Inténtalo más tarde."
            else -> "El backend rechazó la solicitud (HTTP $status)."
        }
        val known = code in setOf("OPENAI_AUTH", "OPENAI_ACCESS", "OPENAI_MODEL", "OPENAI_QUOTA", "OPENAI_TIMEOUT", "OPENAI_NETWORK", "OPENAI_INVALID_SECRET", "OPENAI_UPSTREAM", "BACKEND_AUTH", "BACKEND_RATE_LIMIT")
        return DiagnosticFailure(if (known) code else "BACKEND_HTTP_$status", description)
    }
    fun endpoint(value: String): String {
        val uri = try { URI(value.trim()) } catch (_: Exception) { throw IllegalArgumentException("Introduce una dirección HTTPS válida sin credenciales.") }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) {
            "Introduce una dirección HTTPS válida para tu backend, sin credenciales ni parámetros."
        }
        return uri.toString().trimEnd('/') + "/realtime/client-secret"
    }

    fun secret(body: String, nowSeconds: Long): String {
        try {
            val json = JSONObject(body)
            val value = json.getString("value")
            require(value.matches(Regex("ek_[A-Za-z0-9_-]{6,2048}")))
            require(json.getLong("expires_at") > nowSeconds + 5)
            return value
        } catch (_: Exception) { throw DiagnosticFailure("OPENAI_INVALID_SECRET", "No se recibió una credencial temporal válida y vigente. Vuelve a iniciar la sesión.") }
    }

    fun event(type: String) = JSONObject().put("type", type).toString()

    fun visualQuestion(jpegBase64: String): String = JSONObject()
        .put("type", "conversation.item.create")
        .put("item", JSONObject().put("type", "message").put("role", "user").put("content", JSONArray()
            .put(JSONObject().put("type", "input_text").put("text", "¿Qué estoy viendo? Responde en español de España. Describe solo lo que puedas ver y reconoce la incertidumbre."))
            .put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,$jpegBase64"))))
        .toString()
}

class DiagnosticFailure(val code: String, message: String) : IllegalStateException(message)
