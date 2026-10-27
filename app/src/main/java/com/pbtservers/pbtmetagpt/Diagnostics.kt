package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Solo admite textos definidos por la aplicación. Nunca pasar mensajes/cuerpos externos. */
class Diagnostics(private val journal: ErrorJournal? = null, private val now: () -> Long = System::currentTimeMillis) {
    private val mutable = MutableStateFlow(DiagnosticState())
    val state = mutable.asStateFlow()
    fun record(check: Check, status: CheckStatus, detail: String, code: String = "", manual: Boolean = false) {
        val safeCode = safeCode(code)
        val entry = DiagnosticEntry(check, status, detail, safeCode, now(), manual)
        if (status == CheckStatus.ERROR || status == CheckStatus.WARNING) journal?.record(check,
            if (status == CheckStatus.ERROR) Fault.OBSERVED_ERROR else Fault.SUSPENDED)
        mutable.update { it.copy(entries = it.entries + (check to entry), history = (it.history + entry).takeLast(60)) }
    }
    fun reset() { mutable.value = DiagnosticState() }
    fun report(): String = buildString {
        appendLine("PbtMetaGPT ${BuildConfig.VERSION_NAME} · informe de diagnóstico")
        appendLine("Los estados observados no certifican compatibilidad física. Hora en milisegundos UTC.")
        state.value.entries.values.forEach { appendLine("${it.check.title}: ${it.status.label} | ${it.detail} | ${it.code} | ${it.time} | ${if (it.manual) "confirmación manual" else "observación del sistema"}") }
        appendLine("Últimos eventos:")
        state.value.history.forEach { appendLine("${it.time} ${it.check.title} ${it.status.label} ${it.code}") }
    }
    companion object {
        fun safeCode(value: String): String = value.takeIf {
            it.matches(Regex("[A-Za-z0-9_.-]{1,80}")) && !Regex("sk[-_]|ek_", RegexOption.IGNORE_CASE).containsMatchIn(it)
        }.orEmpty()
        fun sdkCode(error: Any): String = safeCode(if (error is Enum<*>) error.name else error.javaClass.simpleName)
    }
}
