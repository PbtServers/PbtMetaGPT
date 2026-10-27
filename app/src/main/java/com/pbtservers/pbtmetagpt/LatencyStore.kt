package com.pbtservers.pbtmetagpt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Veinte muestras técnicas numéricas; sin identificador de usuario ni contenido. */
class LatencyStore(context: Context) {
    private val prefs = context.getSharedPreferences("latencias_numericas", Context.MODE_PRIVATE)
    fun record(sample: Map<VoiceStage, Long>) {
        val old = runCatching { JSONArray(prefs.getString("samples", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        for (i in maxOf(0, old.length() - 19) until old.length()) next.put(old.getJSONObject(i))
        next.put(JSONObject().apply { sample.forEach { (stage, value) -> put(stage.name, value.coerceIn(0, 3600000)) } })
        prefs.edit().putString("samples", next.toString()).apply()
    }
    fun report(): String = prefs.getString("samples", "[]").orEmpty()
    fun clear() { prefs.edit().clear().apply() }
}
