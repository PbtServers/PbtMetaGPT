package com.pbtservers.pbtmetagpt

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WakeDownloadState(val installed: Boolean, val downloading: Boolean = false, val received: Long = 0,
    val total: Long = -1, val status: String = "", val invalid: Boolean = false)

/** Descarga voluntaria compartida entre Inicio/Ajustes; nunca activa el micrófono. */
class WakeModelDownload(context: Context, private val scope: CoroutineScope) {
    private val model = WakeModel(context)
    private val mutable = MutableStateFlow(snapshot())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private fun snapshot(status: String = "") = WakeDownloadState(model.installed(), status = status, invalid = model.model.exists() && !model.installed())
    fun refresh() { if (job?.isActive != true) mutable.value = snapshot() }
    fun install(source: (() -> java.io.InputStream)? = null) {
        if (job?.isActive == true || model.installed()) return
        mutable.value = snapshot().copy(downloading = true, status = "Preparando descarga")
        job = scope.launch(Dispatchers.IO) {
            try {
                model.install(progress = { received, total, phase -> mutable.value = mutable.value.copy(received = received, total = total, status = phase) }, source = source)
                mutable.value = snapshot("Modelo instalado. Puedes activar la escucha permanente.")
            } catch (cancel: CancellationException) { mutable.value = snapshot("Descarga cancelada. Puedes reintentar."); throw cancel }
            catch (_: Exception) { mutable.value = snapshot(if (!currentCoroutineContext().isActive) "Descarga cancelada. Puedes reintentar." else "No se pudo instalar el modelo. Comprueba conexión y 320 MiB libres; reintenta. Se rechazan archivos con SHA-256 incorrecto.") }
        }
    }
    fun cancel() { job?.cancel(); model.cancelDownload() }
    fun delete() { if (job?.isActive != true && !ContinuousWakeService.running) { model.delete(); refresh() } }
}
