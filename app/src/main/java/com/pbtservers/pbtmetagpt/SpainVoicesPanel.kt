package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*

@Composable fun SpainVoicesPanel(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit) {
    val selected = SpainVoiceCatalog.voices.firstOrNull { it.id == profile.ttsVoice } ?: SpainVoiceCatalog.voice("davefx")
    val store = remember(selected.packageId) { SpainVoiceModel(context.applicationContext, selected.id) }
    val scope = rememberCoroutineScope()
    var busy by remember(selected.packageId) { mutableStateOf(false) }
    var operation by remember(selected.packageId) { mutableStateOf<Job?>(null) }
    var status by remember(selected.packageId) { mutableStateOf("") }
    var installed by remember(selected.packageId) { mutableStateOf(store.installed()) }
    DisposableEffect(selected.packageId) { onDispose { operation?.cancel() } }
    fun manage(action: suspend () -> String) {
        stop()
        operation = scope.launch {
            busy = true
            try { status = action(); installed = store.installed() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { status = "No se pudo completar la operación. Revisa conexión, espacio e integridad y vuelve a intentarlo." }
            finally { busy = false }
        }
    }
    Text("Español de España · Piper local", style = MaterialTheme.typography.titleLarge)
    Text("Voces neuronales · calidad medium · sin Internet después de instalar. Un toque guarda la selección; la muestra se reproduce solo al pedirla.")
    SpainVoiceCatalog.voices.forEach { voice ->
        val ready = if (voice.packageId == selected.packageId) installed else SpainVoiceModel(context, voice.id).installed()
        val target = VoiceModes.spain(profile, voice.id)
        VoiceChoice(voice.name, "Español de España · es-ES", "Piper · ${if (ready) "modelo instalado" else "modelo pendiente de descarga"}",
            profile.ttsEngine == "piper_es" && profile.ttsVoice == voice.id,
            { stop(); change(target) }, { stop(); preview(target) }, ready && !busy)
    }
    Text("Modelo seleccionado: ${selected.name}", style = MaterialTheme.typography.titleMedium)
    Text(if (selected.packageId == SpainVoiceCatalog.packageId)
        "Davefx: 63,2 MB; 256 MiB libres para instalar. Modelo MIT; datos CC0."
        else "Sharvard: 76,7 MB compartidos por las dos voces; 300 MiB libres para instalar. Datos CC BY 3.0: Aubanel, García Lecumberri y Cooke, Sharvard_IJA (2014), DOI 10.7488/ds/75. Modelo publicado en rhasspy/piper-voices; ajuste a partir de Lessac.", style = MaterialTheme.typography.bodySmall)
    Text("Fonemizador eSpeak NG GPL-3.0-or-later. La muestra permite valorar pronunciación y timbre; calidad física pendiente de escuchar en tu teléfono.", style = MaterialTheme.typography.bodySmall)
    if (!installed) Button(onClick = { manage {
        store.install { current, total -> scope.launch { status = "Descargando ${current * 100 / total} %" } }
        "Modelo instalado y SHA-256 comprobado. Pulsa Escuchar muestra."
    } }, enabled = !busy) { Text(if (selected.id == "davefx") "Descargar voz de España" else "Descargar Sharvard · dos voces") }
    if (installed) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { manage { if (store.verify()) "SHA-256 correcto" else "Modelo dañado: elimina y descarga de nuevo." } }, enabled = !busy) { Text("Comprobar modelo") }
        TextButton(onClick = { manage { store.delete(); "Modelo eliminado; la selección se conserva." } }, enabled = !busy) { Text("Eliminar modelo") }
    }
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { operation?.cancel() }) { Text("Cancelar descarga") }
    }
    Text("Velocidad")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(.9 to "Pausada", 1.0 to "Normal", 1.1 to "Ágil").forEach { (speed, label) ->
            FilterChip(kotlin.math.abs(profile.speed - speed) < .01, onClick = { stop(); change(profile.copy(speed = speed)) }, label = { Text(label) })
        }
    }
    Button(onClick = save, enabled = !busy) { Text("Guardar voz") }
    if (status.isNotBlank()) Text(status)
}
