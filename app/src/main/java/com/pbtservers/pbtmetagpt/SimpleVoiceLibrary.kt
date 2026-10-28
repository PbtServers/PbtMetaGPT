package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable fun VoiceLibraryScreen(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit, report: (String) -> Unit, compact: Boolean = false,
) {
    Text("Elige cómo quieres escuchar", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("natural", "rapida", "personalizada").forEach { mode ->
            FilterChip(selected = profile.voiceMode == mode, onClick = { stop(); change(VoiceModes.choose(profile, mode)) },
                label = { Text(VoiceModes.label(mode)) })
        }
    }
    val previewState by (context.applicationContext as PbtApplication).voice.state.collectAsStateWithLifecycle()
    if (previewState.active || previewState.connecting || previewState.responding) {
        TextButton(onClick = stop) { Text("Detener muestra") }
    }
    when (profile.voiceMode) {
        "natural" -> KokoroVoicePanel(context, profile, change, save, preview, stop)
        "rapida" -> AndroidVoicePanel(context, profile, save, preview, stop, change)
        else -> AdvancedVoiceLibraryScreen(context, profile, change, save, preview, stop, report, compact)
    }
}

@Composable private fun AndroidVoicePanel(context: Context, profile: AssistantProfile, save: () -> Unit,
    preview: (AssistantProfile) -> Unit, stop: () -> Unit, change: (AssistantProfile) -> Unit,
) {
    var voices by remember { mutableStateOf(emptyList<AndroidVoice>()) }
    var status by remember { mutableStateOf("Consultando voces instaladas…") }
    LaunchedEffect(profile.language) {
        try {
            voices = AndroidVoiceEngine(context).voices().filter { if (profile.language == "es-ES") it.language.equals("es-ES", true)
                else it.language.substringBefore('-') == profile.language.substringBefore('-') }
                .sortedWith(compareByDescending<AndroidVoice> { it.language.equals("es-ES", true) }.thenBy { it.name })
            status = if (voices.isEmpty()) "Instala una voz ${profile.language} en los ajustes de síntesis de Android." else "Voces del sistema · sin conexión · menor consumo"
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { status = "No se pudieron consultar las voces del sistema." }
    }
    Text("Android TTS rápido", style = MaterialTheme.typography.titleMedium)
    // Conservar la posición del botón mientras Android añade las voces disponibles.
    Button(onClick = save) { Text("Guardar voz") }
    Text(status)
    voices.forEach { local ->
        val target = profile.copy(ttsEngine = "android", ttsVoice = local.name, ttsPackage = local.engine, voiceMode = "rapida")
        VoiceChoice(local.name, local.language, "Android TTS", profile.ttsEngine == "android" && profile.ttsVoice == local.name,
            { stop(); change(target) }, { stop(); preview(target) }, true)
    }
}

@Composable private fun KokoroVoicePanel(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit,
) {
    val peninsular = profile.ttsEngine != "kokoro"
    val store = remember(peninsular) { if (peninsular) SpainVoiceModel(context.applicationContext) else KokoroModel(context.applicationContext) }
    val scope = rememberCoroutineScope()
    var installed by remember(peninsular) { mutableStateOf(store.installed()) }
    var busy by remember(peninsular) { mutableStateOf(false) }
    var operation by remember(peninsular) { mutableStateOf<Job?>(null) }
    var status by remember(peninsular) { mutableStateOf("") }
    DisposableEffect(peninsular) { onDispose { operation?.cancel() } }
    fun manage(action: suspend () -> String) {
        stop(); operation = scope.launch {
            busy = true
            try { status = action(); installed = store.installed() }
            catch (cancel: CancellationException) { status = "Descarga cancelada"; throw cancel }
            catch (_: Exception) { status = "No se pudo completar la operación. Comprueba conexión, integridad y espacio; puedes reintentar." }
            finally { busy = false }
        }
    }
    Text(if (peninsular) "Español de España · Piper local" else "Kokoro local", style = MaterialTheme.typography.titleLarge)
    Text("Voz neuronal · sin Internet después de instalar")
    Text("Natural · recomendada", style = MaterialTheme.typography.labelLarge)
    VoiceChoice("Davefx", "Español de España · es-ES", "Piper neuronal local", peninsular,
        { stop(); change(VoiceModes.spain(profile)) }, { stop(); preview(VoiceModes.spain(profile)) }, peninsular && installed && !busy)
    TextButton(onClick = { stop(); change(VoiceModes.kokoro(profile)) }) { Text("Otras voces locales · Kokoro") }
    if (!peninsular) {
    Text("Kokoro: español sin variante es-ES acreditada. No se ofrece como voz peninsular.", style = MaterialTheme.typography.bodySmall)
    KokoroCatalog.voices.keys.forEach { id ->
        val target = VoiceModes.kokoro(profile, id)
        VoiceChoice(KokoroCatalog.name(id), "Español", "Kokoro-82M", profile.ttsEngine == "kokoro" && profile.ttsVoice == id,
            { stop(); change(target) }, { stop(); preview(target) }, installed && !busy)
    }
    }
    Text("Velocidad")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(0.9 to "Pausada", 1.0 to "Normal", 1.1 to "Ágil").forEach { (speed, label) ->
            FilterChip(kotlin.math.abs(profile.speed - speed) < .01, onClick = { stop(); change(profile.copy(speed = speed)) }, label = { Text(label) })
        }
    }
    Button(onClick = save, enabled = !busy) { Text("Guardar voz") }
    Text(if (peninsular) "Davefx: 63,2 MB; 256 MiB libres para instalar. Modelo MIT; datos CC0. Fonemizador eSpeak NG GPL-3.0-or-later."
        else "Kokoro: 354 MB; 750 MiB libres para instalar. Pesos Apache 2.0; fonemizador eSpeak NG GPL-3.0-or-later.", style = MaterialTheme.typography.bodySmall)
    if (!installed) Button(onClick = { manage {
        store.install { current, total -> scope.launch { status = "Descargando ${current * 100 / total} %" } }
        "Modelo instalado y SHA-256 comprobado. Pulsa Escuchar muestra."
    } }, enabled = !busy) { Text(if (peninsular) "Descargar voz de España" else "Descargar Kokoro local") }
    if (busy) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
        TextButton(onClick = { operation?.cancel() }) { Text("Cancelar") }
    }
    if (installed) Row {
        TextButton(onClick = { manage { if (store.verify()) "Integridad SHA-256 correcta" else "Modelo dañado: elimina y descarga de nuevo." } }, enabled = !busy) { Text("Comprobar modelo") }
        TextButton(onClick = { manage { store.delete(); "Modelo eliminado; tu selección de voz se conserva." } }, enabled = !busy) { Text("Eliminar modelo") }
    }
    if (status.isNotEmpty()) Text(status)
}

@Composable private fun VoiceChoice(name: String, language: String, engine: String, selected: Boolean,
    select: () -> Unit, preview: () -> Unit, available: Boolean,
) {
    ListItem(modifier = Modifier.selectable(selected, role = Role.RadioButton, onClick = select),
        headlineContent = { Text(name) }, supportingContent = { Text("$language · $engine") },
        leadingContent = { RadioButton(selected, onClick = null) },
        trailingContent = { TextButton(onClick = preview, enabled = available,
            modifier = Modifier.semantics { contentDescription = "Escuchar muestra de $name" }) { Text("Escuchar muestra") } })
}
