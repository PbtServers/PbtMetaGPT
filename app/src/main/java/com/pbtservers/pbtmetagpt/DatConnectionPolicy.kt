package com.pbtservers.pbtmetagpt

/** Presupuesto por autorización, sin reinicio por una conexión fugaz ni por volver a la UI. */
class DatConnectionPolicy {
    var authorized = false; private set
    var attempts = 0; private set
    fun authorize() { authorized = true; attempts = 0 }
    fun revoke() { authorized = false }
    fun nextDelay(available: Boolean, ownerActive: Boolean, hfpActive: Boolean): Long? {
        if (!authorized || !available || !ownerActive || hfpActive || attempts >= delays.size) return null
        return delays[attempts++]
    }
    companion object {
        private val delays = longArrayOf(2_000, 5_000, 15_000)
        fun retain(visible: Boolean, service: Boolean, recreating: Boolean) = visible || service || recreating
    }
}
