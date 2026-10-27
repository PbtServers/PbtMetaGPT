package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

@Composable fun VoiceLibraryScreen(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit, report: (String) -> Unit, compact: Boolean = false,
) {
    var advanced by remember { mutableStateOf(false) }
    var voices by remember { mutableStateOf(emptyList<AndroidVoice>()) }
    var status by remember { mutableStateOf("Consultando voces instaladas…") }
    val settings = remember { HostedSettings(context) }
    var authorized by remember { mutableStateOf(settings.qwenAuthorized) }
    LaunchedEffect(profile.voiceMode, profile.language) {
        if (profile.voiceMode == "rapida") try {
            voices = AndroidVoiceEngine(context).voices().filter { it.language.substringBefore('-') == profile.language.substringBefore('-') }.sortedBy { it.name }
            status = if (voices.isEmpty()) "No hay voces locales instaladas para este idioma. Instala una voz en los ajustes TTS de Android o elige Natural." else "Voces locales instaladas · sin enviar texto a un servidor."
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { status = "No se pudo consultar las voces. Revisa los ajustes TTS de Android." }
    }
    Text("Elige cómo quieres escuchar", style = MaterialTheme.typography.titleLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("rapida", "natural", "personalizada").forEach { mode ->
            FilterChip(selected = profile.voiceMode == mode, onClick = { stop(); change(VoiceModes.choose(profile, mode)); advanced = false }, label = { Text(VoiceModes.label(mode)) })
        }
    }
    when (profile.voiceMode) {
        "rapida" -> {
            Text("Android TTS · prioriza una voz local ligera. La disponibilidad y el tiempo de inicio dependen del motor instalado.")
            Text(status, style = MaterialTheme.typography.bodySmall)
            voices.forEachIndexed { index, local ->
                val target = profile.copy(ttsEngine = "android", ttsVoice = local.name, ttsPackage = local.engine, voiceMode = "rapida")
                VoiceChoice("Voz ${index + 1}", java.util.Locale.forLanguageTag(local.language).getDisplayLanguage(java.util.Locale.forLanguageTag("es")), "Android TTS",
                    profile.ttsEngine == "android" && profile.ttsVoice == local.name && profile.ttsPackage == local.engine,
                    { stop(); change(target) }, { stop(); preview(target) }, true)
            }
        }
        "natural" -> {
            Text("Qwen3-TTS · voces neuronales online. Space público experimental: puede haber cola, cuota agotada o suspensión.")
            Row { Checkbox(authorized, {
                stop()
                try { settings.authorizeQwen(it); authorized = it }
                catch (_: Exception) { report("No se pudo guardar el consentimiento de Qwen.") }
            }); Text("Autorizo enviar textos y muestras al Space público Qwen/Qwen3-TTS de Hugging Face, con su cuota anónima y condiciones.") }
            val supported = runCatching { QwenSpaceContract.language(profile.language) }.isSuccess
            if (!supported) Text("Qwen no documenta este idioma. El catalán requiere otro motor compatible.", color = MaterialTheme.colorScheme.error)
            QwenSpaceContract.speakers.forEach { speaker ->
                val target = VoiceModes.choose(profile, "natural").copy(ttsVoice = speaker)
                VoiceChoice(speaker, if (supported) java.util.Locale.forLanguageTag(profile.language).getDisplayLanguage(java.util.Locale.forLanguageTag("es")) else "Idioma no compatible", "Qwen3-TTS · remoto",
                    profile.ttsVoice == speaker, { stop(); change(target) }, { stop(); preview(target) }, authorized && supported)
            }
            Text("Cada muestra consume cuota. Las voces difieren en su idioma de referencia; comprueba su pronunciación en español.", style = MaterialTheme.typography.bodySmall)
        }
        else -> Text("Motores y modelos especiales. RVC sigue experimental sin backend gratuito autorizado verificado; no se suben modelos personales.")
    }
    if (profile.voiceMode != "personalizada") Button(onClick = save) { Text("Guardar voz") }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Cerrar opciones avanzadas" else "Opciones avanzadas de voz") }
    if (advanced) AdvancedVoiceLibraryScreen(context, profile, change, save, preview, stop, report, compact)
}

@Composable private fun VoiceChoice(name: String, language: String, engine: String, selected: Boolean,
    select: () -> Unit, preview: () -> Unit, available: Boolean,
) {
    OutlinedCard(onClick = select, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                RadioButton(selected, onClick = select)
            }
            Text("$language · $engine", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = preview, enabled = available) { Text("Escuchar muestra") }
        }
    }
}
