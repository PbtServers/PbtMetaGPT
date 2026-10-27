package com.pbtservers.pbtmetagpt

import android.content.Context
import org.json.JSONObject

class SetupStore(context: Context) {
    private val prefs = context.getSharedPreferences("configuracion", Context.MODE_PRIVATE)
    init {
        if (!prefs.getBoolean("setup_breve_v2", false)) {
            val old = prefs.getInt("setup_step", 0)
            val next = when (old) { 0, 1 -> 0; 2, 3 -> 1; else -> 2 }
            check(prefs.edit().putInt("setup_step", next).putBoolean("setup_breve_v2", true).commit())
        }
    }
    val step get() = prefs.getInt("setup_step", 0).coerceIn(0, 2)
    fun draft(): AssistantProfile? = prefs.getString("setup_draft", null)?.let {
        AssistantProfile.parse(JSONObject(it), validate = false)
    }
    fun save(step: Int, draft: AssistantProfile) {
        val json = draft.json().toString()
        require(!SecretPatterns.contains(json)) { "No guardes claves API en los campos del asistente." }
        check(prefs.edit().putInt("setup_step", step.coerceIn(0, 2)).putString("setup_draft", json).commit()) {
            "No se pudo guardar el progreso. Comprueba el almacenamiento del teléfono."
        }
    }
    fun finish() {
        check(prefs.edit().putBoolean("bienvenida", true).remove("setup_draft").remove("setup_step").commit())
    }
}
