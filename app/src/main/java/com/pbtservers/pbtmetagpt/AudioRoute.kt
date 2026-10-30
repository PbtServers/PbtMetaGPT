package com.pbtservers.pbtmetagpt

import android.content.Context

/** Concesión de ruta compartida: cerrar un turno no retira la ruta retenida por el servicio. */
class AudioRoute(context: Context, private val onInterrupted: (String) -> Unit) {
    private val music = MusicSettings(context.applicationContext)
    private val session = (context.applicationContext as? PbtApplication)?.audioSession ?: CommunicationAudioSession(context.applicationContext)
    fun start(preferBluetooth: Boolean, speaker: Boolean = false, passive: Boolean = false) =
        session.acquire(this, preferBluetooth, speaker, onInterrupted,
            requireFocus = !passive || !music.enabled, passive = passive)
    fun actualRoute() = session.actualRoute()
    fun close() = session.release(this)
    fun releaseFocusForMusic() = session.releaseFocus(this)
}
