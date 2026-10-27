package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** WebRTC no permite liberar PeerConnection desde la pila de sus observers. */
internal class NativeCallbackQueue(private val scope: CoroutineScope, private val generation: () -> Int) {
    fun dispatch(token: Int, action: () -> Unit) {
        // Sin Main.immediate: siempre abandonar primero la pila JNI/observer.
        scope.launch(Dispatchers.Main) { if (token == generation()) action() }
    }
}
