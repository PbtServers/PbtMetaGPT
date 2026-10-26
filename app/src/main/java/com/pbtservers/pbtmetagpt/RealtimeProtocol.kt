package com.pbtservers.pbtmetagpt

import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

object RealtimeProtocol {
    fun endpoint(value: String): String {
        val uri = URI(value.trim())
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.fragment == null && uri.query == null) {
            "Introduce una dirección HTTPS válida para tu backend, sin credenciales ni parámetros."
        }
        return uri.toString().trimEnd('/') + "/realtime/client-secret"
    }

    fun secret(body: String, nowSeconds: Long): String {
        val json = JSONObject(body)
        val value = json.getString("value")
        require(value.startsWith("ek_") && value.length > 8) { "El backend no devolvió una credencial temporal válida." }
        require(json.getLong("expires_at") > nowSeconds + 5) { "La credencial temporal ha caducado. Vuelve a iniciar la sesión." }
        return value
    }

    fun event(type: String) = JSONObject().put("type", type).toString()

    fun visualQuestion(jpegBase64: String): String = JSONObject()
        .put("type", "conversation.item.create")
        .put("item", JSONObject().put("type", "message").put("role", "user").put("content", JSONArray()
            .put(JSONObject().put("type", "input_text").put("text", "¿Qué estoy viendo? Responde en español de España. Describe solo lo que puedas ver y reconoce la incertidumbre."))
            .put(JSONObject().put("type", "input_image").put("image_url", "data:image/jpeg;base64,$jpegBase64"))))
        .toString()
}
