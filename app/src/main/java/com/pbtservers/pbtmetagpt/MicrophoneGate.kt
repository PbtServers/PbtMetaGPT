package com.pbtservers.pbtmetagpt

enum class MicrophoneOwner { CONTINUOUS_LOCAL, ANDROID_LOCAL, REALTIME, DIAGNOSTIC }

/** Exclusión en todo el proceso; una liberación antigua nunca cede el micrófono de otra sesión. */
class MicrophoneGate {
    class Lease internal constructor(val owner: MicrophoneOwner, internal val generation: Long)
    private var generation = 0L
    private var current: Lease? = null
    @Synchronized fun acquire(owner: MicrophoneOwner): Lease? {
        if (current != null) return null
        return Lease(owner, ++generation).also { current = it }
    }
    @Synchronized fun release(lease: Lease?) { if (lease != null && current == lease) current = null }
    @Synchronized fun owner(): MicrophoneOwner? = current?.owner
}
