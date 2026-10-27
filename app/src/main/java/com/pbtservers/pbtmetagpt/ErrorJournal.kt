package com.pbtservers.pbtmetagpt

import java.io.File
import java.io.IOException

enum class Fault { OBSERVED_ERROR, SUSPENDED, INITIALIZATION, RELEASE, UNCAUGHT, PROCESS_NATIVE, PROCESS_SYSTEM,
    WEBRTC_INITIALIZE, AUDIO_MODULE_CREATE, PEER_FACTORY_CREATE, RECOGNIZER_CREATE, CREDENTIAL_REQUEST,
    AUTHENTICATION, QUOTA, NETWORK, TLS, SESSION_REJECTED }

/** Solo códigos definidos en fuentes: sin mensajes externos, URL, hilo ni contenido del usuario. */
class ErrorJournal(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private val current get() = File(directory, "errores.txt")
    private val previous get() = File(directory, "anteriores.txt")
    private val crash get() = File(directory, "ultimo-cierre.txt")
    @Synchronized fun record(component: Check, fault: Fault, problem: Throwable? = null) {
        val line = "${java.time.Instant.ofEpochMilli(now())} | ${component.name} | ${fault.name} | ${safeType(problem)} | ${frames(problem)}\n"
        try {
            directory.mkdirs(); expire()
            if (current.length() + line.toByteArray().size > MAX_BYTES) {
                previous.delete()
                if (!current.renameTo(previous)) current.delete()
            }
            current.appendText(line, Charsets.UTF_8)
            if (fault == Fault.UNCAUGHT) crash.writeText(line, Charsets.UTF_8)
        } catch (_: IOException) {
            // Un disco lleno no debe sustituir el fallo original por otro cierre.
        }
    }
    @Synchronized fun report(): String {
        expire()
        return buildString {
            appendLine("PbtMetaGPT · registros técnicos sin contenido privado")
            appendLine("Fechas UTC. Retención: 7 días; máximo 3 × 64 KiB.")
            appendLine("Sin audio, imágenes, conversaciones, URL, identificadores ni mensajes externos.")
            listOf(crash, previous, current).forEach { file ->
                if (file.isFile) append(file.bufferedReader(Charsets.UTF_8).use { it.readText().take(MAX_BYTES) })
            }
        }
    }
    @Synchronized fun clear() { listOf(current, previous, crash).forEach { it.delete() } }
    private fun expire() { listOf(current, previous, crash).filter { it.exists() && now() - it.lastModified() > RETENTION }.forEach { it.delete() } }
    companion object {
        const val MAX_BYTES = 65536
        const val RETENTION = 7L * 24 * 60 * 60 * 1000
        fun safeType(problem: Throwable?): String = when (problem) {
            null -> "SinExcepcion"
            is SecurityException -> "SecurityException"
            is IllegalStateException -> "IllegalStateException"
            is IllegalArgumentException -> "IllegalArgumentException"
            is LinkageError -> "LinkageError"
            is OutOfMemoryError -> "OutOfMemoryError"
            is IOException -> "IOException"
            else -> "OtroFallo"
        }
        fun frames(problem: Throwable?): String = problem?.stackTrace.orEmpty()
            .filter { it.className.startsWith("com.pbtservers.pbtmetagpt.") }
            .take(12).mapNotNull { frame -> frame.fileName?.takeIf { it.matches(Regex("[A-Za-z0-9_]+\\.kt")) }
                ?.let { "$it:${frame.lineNumber.coerceIn(0, 100000)}" } }.joinToString(",")
    }
}
