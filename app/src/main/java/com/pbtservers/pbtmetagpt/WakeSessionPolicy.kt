package com.pbtservers.pbtmetagpt

enum class ListeningPhase(val label: String) {
    OFF("Desactivado"), LOADING("Preparando motor local"), LISTENING("Esperando frase de activación"),
    DETECTED("Frase detectada"), PREPARING("Preparando proveedor elegido"), CONVERSATION("Conversación activa"),
    RETURNING("Volviendo a escucha local"), SUSPENDED("Escucha suspendida · pulsa Activar escucha para reintentar"),
}

/** Consentimiento y callbacks de una sola generación, sin depender de una pantalla. */
class WakeSessionPolicy {
    var authorized = false; private set
    var phase = ListeningPhase.OFF; private set
    private var epoch = 0
    private var hadSession = false
    fun authorize() { epoch++; authorized = true; hadSession = false; phase = ListeningPhase.LOADING }
    fun beginListening(): Int? {
        if (!authorized || phase !in setOf(ListeningPhase.LOADING, ListeningPhase.RETURNING)) return null
        phase = ListeningPhase.LOADING
        return ++epoch
    }
    fun ready(token: Int): Boolean {
        if (!authorized || token != epoch || phase != ListeningPhase.LOADING) return false
        phase = ListeningPhase.LISTENING; return true
    }
    fun captureCurrent(token: Int) = authorized && token == epoch && phase in setOf(ListeningPhase.LOADING, ListeningPhase.LISTENING)
    fun detected(token: Int): Boolean {
        if (!authorized || token != epoch || phase != ListeningPhase.LISTENING) return false
        phase = ListeningPhase.DETECTED; return true
    }
    fun preparing() {
        // Preparar la salida de otro turno no deshace una conversación ya confirmada.
        if (authorized && phase != ListeningPhase.CONVERSATION) { epoch++; phase = ListeningPhase.PREPARING }
    }
    /** Una orden local no abre proveedor ni concede una nueva autorización. */
    fun localCommandFinished(): Boolean {
        if (!authorized || phase != ListeningPhase.DETECTED) return false
        epoch++; phase = ListeningPhase.RETURNING; return true
    }
    /** true solo cuando una conversación real ha terminado y puede volver a escuchar. */
    fun onVoice(active: Boolean, connecting: Boolean, failed: Boolean, paused: Boolean = false): Boolean {
        if (!authorized) return false
        // StateFlow conserva el error del proveedor anterior al autorizar otra escucha.
        if ((failed || paused) && (hadSession || phase in setOf(ListeningPhase.DETECTED, ListeningPhase.PREPARING))) {
            suspendListening(); return false
        }
        if (failed || paused) return false
        if (active) { hadSession = true; phase = ListeningPhase.CONVERSATION }
        else if (!connecting && hadSession) { hadSession = false; phase = ListeningPhase.RETURNING; return true }
        // La captura puede estar liberándose antes de publicar connecting. El watchdog
        // del servicio acota esta transición; un estado anterior no la cancela.
        return false
    }
    fun suspendListening() { epoch++; authorized = false; hadSession = false; phase = ListeningPhase.SUSPENDED }
    fun stop() { epoch++; authorized = false; hadSession = false; phase = ListeningPhase.OFF }
}
