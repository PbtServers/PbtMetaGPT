package com.pbtservers.pbtmetagpt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Hasta 120 eventos técnicos: permite veinte ciclos completos, sin contenido ni identificador personal. */
class LatencyStore(context: Context) {
    private val prefs = context.getSharedPreferences("latencias_numericas", Context.MODE_PRIVATE)
    fun record(sample: Map<VoiceStage, Long>) {
        val old = runCatching { JSONArray(prefs.getString("samples", "[]")) }.getOrDefault(JSONArray())
        val next = JSONArray()
        for (i in maxOf(0, old.length() - 119) until old.length()) next.put(old.getJSONObject(i))
        next.put(JSONObject().apply { sample.forEach { (stage, value) -> put(stage.name, value.coerceIn(0, 3600000)) } })
        prefs.edit().putString("samples", next.toString()).apply()
    }
    fun report(): String = prefs.getString("samples", "[]").orEmpty()
    fun clear() { prefs.edit().clear().apply() }
}
