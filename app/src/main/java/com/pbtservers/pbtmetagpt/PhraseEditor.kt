package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PhraseEditor(profile: AssistantProfile, change: (AssistantProfile) -> Unit, test: (ActivationPhrase) -> Unit, stop: () -> Unit) {
    var consent by remember { mutableStateOf(false) }
    var heard by remember { mutableStateOf("") }
    var page by remember(profile.id) { mutableIntStateOf(0) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    val primary = profile.activationPhrases.firstOrNull { it.primary } ?: profile.activationPhrases.firstOrNull() ?: ActivationPhrase(id = "principal", primary = true)
    fun replacePrimary(value: ActivationPhrase) = change(profile.copy(activationPhrases =
        if (profile.activationPhrases.any { it.id == primary.id }) profile.activationPhrases.map { if (it.id == primary.id) value else it }
        else profile.activationPhrases + value))
    OutlinedTextField(primary.rendered(profile.name), { if (it.length <= 160) replacePrimary(primary.copy(text = it, appendName = false)) },
        label = { Text("Frase de activación") }, modifier = Modifier.fillMaxWidth())
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Frase activada"); Switch(primary.enabled, { replacePrimary(primary.copy(enabled = it)) })
    }
    Text("Sensibilidad de coincidencia")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("baja" to "Baja", "normal" to "Normal", "alta" to "Alta").forEach { (id, label) ->
            FilterChip(profile.wakeSensitivity == id, onClick = { stop(); change(profile.copy(wakeSensitivity = id, tolerantActivation = id == "alta")) }, label = { Text(label) })
        }
    }
    OutlinedButton(onClick = { test(primary) }, enabled = primary.enabled && primary.rendered(profile.name).isNotBlank()) { Text("Probar frase") }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar configuración avanzada" else "Configuración avanzada de frases") }
    if (!advanced) return
    val last = ((profile.activationPhrases.size - 1).coerceAtLeast(0)) / 10
    val shown = page.coerceAtMost(last)
    Text("Frases de activación", style = MaterialTheme.typography.titleLarge)
    Text("No hay un límite de número de frases. El conjunto de perfiles debe caber en los 256 KiB del formato de almacenamiento/importación. Cada frase admite 160 caracteres para mantener acotada la ventana de reconocimiento.")
    Text("Página ${shown + 1}/${last + 1} · ${profile.activationPhrases.size} frases en total. La paginación solo limita lo dibujado, no la detección.")
    Row {
        TextButton(onClick = { page = shown - 1 }, enabled = shown > 0) { Text("Página anterior") }
        TextButton(onClick = { page = shown + 1 }, enabled = shown < last) { Text("Página siguiente") }
    }
    profile.activationPhrases.drop(shown * 10).take(10).forEach { phrase -> key(phrase.id) {
        fun update(value: ActivationPhrase) = change(profile.copy(activationPhrases = profile.activationPhrases.map { if (it.id == phrase.id) value else it }))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(phrase.text, { if (it.length <= 160) update(phrase.copy(text = it)) }, label = { Text("Texto de la frase") }, modifier = Modifier.fillMaxWidth())
            Text("Vista previa: ${phrase.rendered(profile.name)}")
            Row { Checkbox(phrase.appendName, { update(phrase.copy(appendName = it)) }); Text("Añadir automáticamente el nombre") }
            Row { Checkbox(phrase.enabled, { update(phrase.copy(enabled = it)) }); Text("Frase activada") }
            Row { RadioButton(phrase.primary, { change(profile.copy(activationPhrases = profile.activationPhrases.map { it.copy(primary = it.id == phrase.id) })) }); Text("Frase principal") }
            TextButton(onClick = { change(profile.copy(activationPhrases = profile.activationPhrases.filterNot { it.id == phrase.id })) }) { Text("Eliminar frase") }
            OutlinedButton(onClick = { test(phrase) }, enabled = consent && phrase.enabled && phrase.rendered(profile.name).isNotBlank()) { Text("Probar esta frase con micrófono") }
        } }
    } }
    Button(onClick = { page = profile.activationPhrases.size / 10; change(profile.copy(activationPhrases = profile.activationPhrases + ActivationPhrase(text = "", appendName = false))) }) { Text("Añadir frase") }
    Row { Checkbox(profile.tolerantActivation, { change(profile.copy(tolerantActivation = it)) }); Text("Tolerar una diferencia textual en la última palabra larga") }
    Text("Esto no ajusta sensibilidad acústica. Las frases de una sola palabra y las parecidas aumentan el riesgo de activaciones accidentales. El motor Android no expone un umbral de sensibilidad.")
    OutlinedTextField(heard, { heard = it.take(160) }, label = { Text("Comprobar comparación escrita (sin micrófono)") }, modifier = Modifier.fillMaxWidth())
    if (heard.isNotBlank()) Text(if (PhraseMatcher.resolve(heard, listOf(profile)) != null) "El texto coincide con una frase activa" else "El texto no coincide")
    Row { Checkbox(consent, { consent = it; if (!it) stop() }); Text("Autorizo el micrófono para una prueba local, sin OpenAI") }
    OutlinedButton(onClick = { consent = false; stop() }) { Text("Detener prueba local") }
}
