package com.pbtservers.pbtmetagpt

import org.vosk.Model

/** Modelo inmutable compartido; cada captura conserva sus propios reconocedores. */
class VoskModels : AutoCloseable {
    private var model: Model? = null
    private var path = ""
    private var leases = 0
    class Lease internal constructor(val model: Model, private val release: () -> Unit) : AutoCloseable {
        private var closed = false
        @Synchronized override fun close() { if (!closed) { closed = true; release() } }
    }
    @Synchronized fun acquire(location: String): Lease {
        check(path.isEmpty() || path == location)
        val current = model ?: Model(location).also { model = it; path = location }
        leases++
        return Lease(current) { synchronized(this) { leases-- } }
    }
    @Synchronized override fun close() {
        if (leases != 0) return
        model?.close(); model = null; path = ""
    }
}
