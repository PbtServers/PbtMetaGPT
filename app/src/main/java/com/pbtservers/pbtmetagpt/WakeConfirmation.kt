package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.sin

/** Aviso audible propio, encaminado por la misma ruta de comunicación que la respuesta. */
object WakeConfirmation {
    private const val SAMPLE_RATE = 16000
    private const val SOUND_MS = 190

    fun pcm(): PcmAudio {
        val frames = SAMPLE_RATE * SOUND_MS / 1000
        val data = ByteArray(frames * 2)
        for (i in 0 until frames) {
            val fade = minOf(1.0, i / 160.0, (frames - i) / 160.0)
            val sample = (sin(2.0 * PI * 740.0 * i / SAMPLE_RATE) * 12000 * fade).toInt()
            data[2 * i] = sample.toByte()
            data[2 * i + 1] = (sample shr 8).toByte()
        }
        return PcmAudio(data, SAMPLE_RATE)
    }

    suspend fun play(context: Context, preferBluetooth: Boolean, preparedRoute: Boolean = false) = withContext(Dispatchers.IO) {
        // El servicio ya retiene su ruta: no renegociar Bluetooth/foco por 190 ms.
        val route = if (preparedRoute) null else AudioRoute(context) {}
        try {
            route?.start(preferBluetooth)
            // El servicio ya retiene su ruta: no añadir 300 ms en cada activación.
            PcmPlayer().playStream(flowOf(pcm()), preRollMs = if (preferBluetooth && !preparedRoute) 300 else 0)
        } finally {
            route?.close()
        }
    }
}
