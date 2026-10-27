package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.*
import java.io.File

@Composable
fun VoiceLibraryScreen(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit, report: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val store = remember { VoicePackageStore(File(context.filesDir, "voces")) }
    var packages by remember { mutableStateOf(emptyList<VoicePackage>()) }
    var voices by remember { mutableStateOf(emptyList<AndroidVoice>()) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var rights by remember { mutableStateOf(false) }
    val settings = remember { VoiceApiSettings(context) }
    var config by remember { mutableStateOf(settings.load()) }
    var externalConsent by remember { mutableStateOf(false) }
    var token by remember { mutableStateOf("") }
    val vault = remember { KeyVault(context, externalTts = true) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { token = ""; operation?.cancel() } }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); operation?.cancel() }
    }
    suspend fun reload() { packages = withContext(Dispatchers.IO) { store.list() } }
    LaunchedEffect(Unit) { reload() }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            stop(); operation = scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) { val job = currentCoroutineContext(); context.contentResolver.openInputStream(uri)?.use { store.install(it) { job.ensureActive() } } ?: error("Sin archivo") }
                    reload(); report("Paquete instalado tras validación estructural. Prueba su inferencia antes de usarlo; no acredita calidad de voz ni derechos.")
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { report("Paquete rechazado: revisa formato, integridad, hashes, metadatos, diccionario, autorización y almacenamiento disponible.") }
                finally { busy = false }
            }
        }
    }
    Text("Biblioteca de voces", style = MaterialTheme.typography.headlineMedium)
    Text("Las voces son síntesis de IA. Utiliza voces originales o autorizadas. No se incluyen modelos ni clonaciones de actores. Una importación válida no garantiza inferencia ni calidad.")
    listOf("realtime" to "OpenAI Realtime · audio nativo", "android" to "Android TTS · voces locales instaladas", "openai_tts" to "OpenAI Speech · transitorio hasta 06/01/2027", "external" to "API TTS externa · contrato PCM", "sherpa_vits" to "Modelo Piper/VITS · Sherpa local").forEach { (id, title) ->
        FilterChip(selected = profile.ttsEngine == id, onClick = { stop(); change(profile.copy(ttsEngine = id, ttsVoice = "", ttsPackage = "", pitch = 1.0)) }, label = { Text(title) })
    }
    Text("Motor del perfil: ${profile.ttsEngine}. Guarda y abre una sesión nueva para aplicar los cambios.")
    if (profile.ttsEngine in listOf("realtime", "openai_tts")) {
        Text("Voces oficiales")
        RealtimeSettings.voices.forEach { id -> FilterChip(selected = profile.voice == id, onClick = { change(profile.copy(voice = id)) }, label = { Text(id) }) }
        if (profile.ttsEngine == "openai_tts") Text("Este endpoint está anunciado como obsoleto y requiere tu clave BYOK de OpenAI, incluso si la conversación usa servidor. La muestra y la síntesis consumen cuota adicional. Usa Realtime para evitar esta ruta transitoria.")
        OutlinedTextField(profile.tone, { change(profile.copy(tone = it.take(4000))) }, label = { Text("Instrucciones de tono y expresión") }, modifier = Modifier.fillMaxWidth())
    }
    if (profile.ttsEngine == "android") {
        Text("Solo se ofrecen voces que el motor declara instaladas y sin requisito de red. Su comportamiento y privacidad dependen también del proveedor Android.")
        Button(onClick = { stop(); operation = scope.launch {
            busy = true
            try { voices = AndroidVoiceEngine(context).voices(); if (voices.isEmpty()) report("No se han encontrado voces locales instaladas. Instala un motor y su idioma en los ajustes de síntesis de Android.") }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { report("No se pudieron consultar los motores TTS instalados.") }
            finally { busy = false }
        } }, enabled = !busy) { Text("Consultar voces locales instaladas") }
        voices.forEach { voice -> FilterChip(selected = profile.ttsVoice == voice.name && profile.ttsPackage == voice.engine,
            onClick = { change(profile.copy(ttsVoice = voice.name, ttsPackage = voice.engine, language = voice.language)) },
            label = { Text("${voice.name} · ${voice.language} · ${voice.engine}") }) }
        Text("Tono solicitado al motor Android: ${"%.2f".format(java.util.Locale.ROOT, profile.pitch)}×. Debe comprobarse acústicamente en ese motor.")
        Slider(profile.pitch.toFloat(), { change(profile.copy(pitch = it.toDouble())) }, valueRange = 0.5f..2f)
    }
    if (profile.ttsEngine == "external") {
        Text("El proveedor recibirá el texto completo de la respuesta. La app no reenvía tu clave OpenAI: usa una credencial separada para este proveedor. Contrato: POST JSON model/voice/input/speed/response_format=pcm; respuesta PCM16 mono little-endian sin cabecera, con Content-Type audio/pcm o application/octet-stream. No todas las API TTS cumplen este contrato.")
        OutlinedTextField(config.endpoint, { config = config.copy(endpoint = it.take(1000)) }, label = { Text("URL HTTPS completa de la API de síntesis") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(config.model, { config = config.copy(model = it.take(100)) }, label = { Text("Modelo por defecto del proveedor") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.ttsPackage, { change(profile.copy(ttsPackage = it.take(100))) }, label = { Text("Modelo específico del perfil (opcional)") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(profile.ttsVoice, { change(profile.copy(ttsVoice = it.take(200))) }, label = { Text("Identificador de voz del proveedor") }, modifier = Modifier.fillMaxWidth())
        Text("Frecuencia PCM declarada por el proveedor")
        listOf(16000, 22050, 24000, 44100, 48000).forEach { rate -> FilterChip(selected = config.sampleRate == rate, onClick = { config = config.copy(sampleRate = rate) }, label = { Text("$rate Hz") }) }
        OutlinedTextField(token, { token = it.take(1024) }, label = { Text("Token del proveedor (se guarda cifrado)") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
        Row { Checkbox(externalConsent, { externalConsent = it }); Text("Autorizo enviar las respuestas al proveedor configurado") }
        Button(onClick = { stop(); runCatching { config.validate(); if (token.isNotBlank()) vault.save(token); settings.save(config); token = "" }
            .onSuccess { report("Conexión del proveedor guardada en este teléfono. La credencial no se exporta con el perfil.") }.onFailure { report("No se pudo guardar la API. Comprueba HTTPS, modelo, token y frecuencia.") } }, enabled = externalConsent) { Text("Guardar proveedor y autorización") }
        OutlinedButton(onClick = { stop(); runCatching { vault.delete() }.onSuccess { token = ""; report("Token externo eliminado.") }.onFailure { report("No se pudo eliminar el token externo.") } }) { Text("Eliminar token externo") }
        Text("La conexión al proveedor se configura por teléfono; modelo y voz se guardan por asistente. Velocidad requiere que el proveedor admita el campo speed. No hay controles acústicos adicionales sin contrato implementado.")
    }
    if (profile.ttsEngine == "sherpa_vits") {
        Text("Solo paquetes ZIP sherpa-piper-phonemes-v1 con modelo, símbolos, pronunciaciones y manifiesto. No se ejecutan instaladores ni bibliotecas del paquete. El diccionario debe cubrir cada palabra; no se incluye un fonemizador universal.")
        Row { Checkbox(rights, { rights = it }); Text("Dispongo de autorización y he revisado licencia y procedencia del modelo") }
        Button(onClick = { import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = rights && !busy) { Text("Importar paquete de voz compatible") }
        if (packages.isEmpty()) Text("No hay modelos instalados. Consulta docs/BIBLIOTECA_DE_VOCES.md para preparar el paquete; un ONNX aislado no es suficiente.")
        packages.forEach { pack -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(pack.name, style = MaterialTheme.typography.titleMedium)
            Text("${pack.format} · ${pack.engine} · ${pack.language}")
            Text("RAM declarada: ${pack.minRamMb} MiB · modelo: ${pack.bytes / 1048576} MiB · CPU arm64-v8a/x86_64")
            Text("Licencia: ${pack.license}\nProcedencia: ${pack.source}")
            Text("Estructura validada. La inferencia y la escucha son comprobaciones separadas y dependen del dispositivo.")
            Button(onClick = { change(profile.copy(ttsVoice = pack.id, language = pack.language)) }) { Text(if (profile.ttsVoice == pack.id) "Modelo seleccionado" else "Seleccionar modelo") }
            OutlinedButton(onClick = { stop(); preview(profile.copy(ttsEngine = "sherpa_vits", ttsVoice = pack.id)) }, enabled = !busy && profile.name.isNotBlank()) { Text("Probar inferencia y muestra de este modelo") }
            TextButton(onClick = { stop(); operation = scope.launch { busy = true; try { withContext(Dispatchers.IO) { store.delete(pack.id) }; reload(); report("Modelo eliminado y almacenamiento liberado. Los perfiles que lo referencian necesitan elegir otra voz.") } catch (_: Exception) { report("No se pudo eliminar el modelo.") } finally { busy = false } } }, enabled = !busy) { Text("Eliminar modelo y liberar espacio") }
        } } }
    }
    Text("Velocidad: ${"%.2f".format(java.util.Locale.ROOT, profile.speed)}×")
    Slider(profile.speed.toFloat(), { change(profile.copy(speed = it.toDouble())) }, valueRange = 0.25f..1.5f)
    OutlinedTextField(profile.language, { change(profile.copy(language = it.take(30))) }, label = { Text("Idioma del perfil") }, modifier = Modifier.fillMaxWidth())
    Button(onClick = { stop(); save() }, enabled = !busy) { Text("Guardar motor y voz en el asistente") }
    Button(onClick = { stop(); preview(profile) }, enabled = !busy && profile.name.isNotBlank()) { Text("Escuchar muestra (puede consumir API)") }
    OutlinedButton(onClick = { operation?.cancel(); stop() }) { Text("Cancelar operación y audio pendiente") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    Text("La conversación alternativa espera el texto y la síntesis antes de reproducir. Durante la reproducción se apaga su micrófono para evitar realimentación; puedes interrumpir con el botón. No se promete la ruta de las gafas sin probarla.")
}
