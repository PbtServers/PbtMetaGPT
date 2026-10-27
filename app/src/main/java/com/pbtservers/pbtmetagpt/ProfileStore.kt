package com.pbtservers.pbtmetagpt

import android.content.Context

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("asistentes", Context.MODE_PRIVATE)
    fun load(): List<AssistantProfile> = prefs.getString("profiles", null)?.let { AssistantProfile.import(it) } ?: emptyList()
    fun selected(): String? = prefs.getString("selected", null)
    fun save(profiles: List<AssistantProfile>, selected: String?) {
        require(profiles.size <= 50) { "Puedes guardar hasta 50 asistentes." }
        val text = if (profiles.isEmpty()) null else AssistantProfile.export(profiles)
        check(prefs.edit().putString("profiles", text).putString("selected", selected).commit()) { "No se pudieron guardar los perfiles." }
    }
}
