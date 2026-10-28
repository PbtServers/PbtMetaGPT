package com.pbtservers.pbtmetagpt

import android.content.Context

/** Recordar una preferencia nunca inicia sensores ni servicios. */
class ListeningSettings(context: Context) {
    private val preferences = context.getSharedPreferences("voz_escucha", Context.MODE_PRIVATE)
    fun load() = ListeningOptions(
        continuous = preferences.getBoolean("continua", true),
        background = preferences.getBoolean("segundo-plano", false),
        interruption = preferences.getBoolean("interrupcion", true),
        recognizeStop = preferences.getBoolean("para", true),
        sensitivity = preferences.getFloat("sensibilidad", 0.5f),
        inactivitySeconds = preferences.getInt("inactividad", 0),
        endPhrase = preferences.getString("finalizacion", "termina la conversación").orEmpty(),
        goodbye = preferences.getBoolean("despedida", false),
    )
    fun save(value: ListeningOptions) {
        value.validated()
        check(preferences.edit().putBoolean("continua", value.continuous).putBoolean("segundo-plano", value.background)
            .putBoolean("interrupcion", value.interruption).putBoolean("para", value.recognizeStop)
            .putFloat("sensibilidad", value.sensitivity).putInt("inactividad", value.inactivitySeconds)
            .putString("finalizacion", value.endPhrase).putBoolean("despedida", value.goodbye).commit())
    }
}
