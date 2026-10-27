package com.pbtservers.pbtmetagpt

import android.content.Context
import org.webrtc.PeerConnectionFactory

/** WebRTC requiere inicialización global del proceso, no por cada reconexión. */
internal class OnceInitializer(private val initialize: () -> Unit) {
    private var completed = false

    @Synchronized fun ensure() {
        if (completed) return
        initialize()
        completed = true
    }
}

internal object WebRtcBootstrap {
    private val once = OnceInitializer {
        // El contexto de aplicación nunca retiene una Activity.
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext)
                .createInitializationOptions()
        )
    }
    private lateinit var applicationContext: Context

    @Synchronized fun ensure(context: Context) {
        applicationContext = context.applicationContext
        once.ensure()
    }
}
