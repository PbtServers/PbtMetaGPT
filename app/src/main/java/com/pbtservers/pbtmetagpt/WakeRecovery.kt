package com.pbtservers.pbtmetagpt

/** Códigos estables de SpeechRecognizer. Silencio no es un fallo del motor. */
object WakeRecovery {
    fun delayMillis(error: Int, consecutiveFailures: Int): Long? = when (error) {
        6, 7 -> 180L // timeout de habla / sin coincidencia: seguir hasta el límite autorizado
        3, 5, 8, 10, 11 -> if (consecutiveFailures <= 4) (350L shl (consecutiveFailures - 1).coerceIn(0, 3)) else null
        else -> null // permisos, idioma y red no se reintentan silenciosamente
    }
}
