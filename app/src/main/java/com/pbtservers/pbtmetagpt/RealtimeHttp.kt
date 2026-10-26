package com.pbtservers.pbtmetagpt

import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

internal data class HttpReply(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Consume y cierra el cuerpo en el hilo IO de OkHttp antes de volver a la interfaz. */
internal suspend fun Call.awaitReply(): HttpReply = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            if (continuation.isActive) continuation.resumeWithException(e)
        }
        override fun onResponse(call: Call, response: Response) {
            try {
                val reply = response.use { HttpReply(it.code, it.body?.string().orEmpty()) }
                if (continuation.isActive) continuation.resume(reply)
            } catch (problem: Exception) {
                if (continuation.isActive) continuation.resumeWithException(problem)
            }
        }
    })
}
