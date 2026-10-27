package com.pbtservers.pbtmetagpt

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

/** Memoria de pantalla: no persiste fotos en disco ni reanuda envíos al recrear. */
class PhotoMemory : ViewModel() {
    var bitmap by mutableStateOf<Bitmap?>(null)
    var error by mutableStateOf<String?>(null)
    override fun onCleared() { bitmap = null; error = null }
}

enum class PhotoAnalysisStatus(val message: String) {
    NO_IMAGE("Haz una foto para preparar el análisis."),
    NEEDS_CONFIGURATION("Imagen conservada. Configura OpenAI y guarda un asistente desde Más ajustes."),
    PROCESSING("Procesando. Puedes cancelar la conexión o esperar a que termine la respuesta."),
    PAUSED("Imagen preparada. Reanuda o finaliza la sesión antes de analizarla."),
    READY("Imagen preparada. No necesitas iniciar una conversación antes: se conectará tras tu confirmación, con el micrófono apagado."),
    ERROR("El análisis no se completó. La foto sigue disponible para reintentar."),
}

object PhotoAnalysisPolicy {
    fun status(hasImage: Boolean, configured: Boolean, voice: VoiceState, failed: Boolean = false): PhotoAnalysisStatus = when {
        !hasImage -> PhotoAnalysisStatus.NO_IMAGE
        !configured -> PhotoAnalysisStatus.NEEDS_CONFIGURATION
        voice.connecting || voice.responding || voice.sendingImage -> PhotoAnalysisStatus.PROCESSING
        voice.active && voice.paused -> PhotoAnalysisStatus.PAUSED
        failed -> PhotoAnalysisStatus.ERROR
        else -> PhotoAnalysisStatus.READY
    }
    fun canAnalyze(status: PhotoAnalysisStatus) = status == PhotoAnalysisStatus.READY || status == PhotoAnalysisStatus.ERROR
}
