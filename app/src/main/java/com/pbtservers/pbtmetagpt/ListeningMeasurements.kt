package com.pbtservers.pbtmetagpt

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.os.SystemClock
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject

/** Solo números del proceso; sin nombres de dispositivos Bluetooth, PCM o transcripciones. */
class ListeningMeasurements(context: Context) {
    private val preferences = context.getSharedPreferences("medidas_escucha", Context.MODE_PRIVATE)
    suspend fun observe(passive: () -> Boolean, inputType: () -> Int) {
        var real = SystemClock.elapsedRealtime()
        var cpu = Process.getElapsedCpuTime()
        while (currentCoroutineContext().isActive) {
            val wasPassive = passive()
            delay(30000)
            val now = SystemClock.elapsedRealtime()
            val consumed = Process.getElapsedCpuTime()
            if (wasPassive && passive()) {
                val pss = withContext(Dispatchers.IO) { Debug.getPss() }
                val sample = JSONObject().put("api", Build.VERSION.SDK_INT).put("entrada_tipo", inputType())
                    .put("ventana_ms", now - real).put("cpu_ms", consumed - cpu).put("pss_kib", pss)
                    .put("reloj_ms", now)
                val previous = runCatching { JSONArray(preferences.getString("samples", "[]")) }.getOrDefault(JSONArray())
                val next = JSONArray()
                for (i in maxOf(0, previous.length() - 59) until previous.length()) next.put(previous.getJSONObject(i))
                next.put(sample)
                preferences.edit().putString("samples", next.toString()).apply()
            }
            real = now; cpu = consumed
        }
    }
    fun report(): String = preferences.getString("samples", "[]").orEmpty()
}
