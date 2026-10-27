package com.pbtservers.pbtmetagpt

/** Devuelve categorías cerradas, nunca el código externo o su cuerpo. */
object FaultClassifier {
    fun connection(code: String): Fault = when {
        code.endsWith("_401") || code.endsWith("_403") || code.endsWith("_AUTH") || code.startsWith("KEY") -> Fault.AUTHENTICATION
        code.endsWith("_429") || code.contains("QUOTA") || code.contains("RATE_LIMIT") -> Fault.QUOTA
        code == "TLS_ERROR" -> Fault.TLS
        code.contains("NETWORK") || code.contains("TIMEOUT") || code == "DNS_ERROR" -> Fault.NETWORK
        code.startsWith("SDP") || code == "OPENAI_INVALID_SECRET" -> Fault.SESSION_REJECTED
        else -> Fault.INITIALIZATION
    }
}
