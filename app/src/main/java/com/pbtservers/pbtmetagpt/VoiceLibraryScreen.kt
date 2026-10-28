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
fun AdvancedVoiceLibraryScreen(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    save: () -> Unit, preview: (AssistantProfile) -> Unit, stop: () -> Unit, report: (String) -> Unit, compact: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    val store = remember { VoicePackageStore(File(context.filesDir, "voces")) }
    val rvcStore = remember { RvcModelStore(File(context.filesDir, "modelos_rvc")) }
    val rvcOnnx = remember { RvcOnnxStore(context) }
    var rvcOnnxReady by remember { mutableStateOf(rvcOnnx.ready()) }
    var rvcOnnxEnabled by remember { mutableStateOf(rvcOnnx.enabled) }
    var packages by remember { mutableStateOf(emptyList<VoicePackage>()) }
    var rvcModels by remember { mutableStateOf(rvcStore.list()) }
    var selectedRvcIndex by remember { mutableStateOf<String?>(null) }
    var voices by remember { mutableStateOf(emptyList<AndroidVoice>()) }
    var busy by remember { mutableStateOf(false) }
    var operation by remember { mutableStateOf<Job?>(null) }
    var rights by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(!compact) }
    var engineMenu by remember { mutableStateOf(false) }
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
    LaunchedEffect(profile.ttsEngine) {
        if (profile.ttsEngine == "sherpa_vits") {
            try { reload() }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: java.io.IOException) { report("No se pudo leer la biblioteca local. Comprueba el almacenamiento y vuelve a intentarlo.") }
            catch (_: SecurityException) { report("Android no permite acceder a la biblioteca local.") }
        }
    }
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
    val importRvc = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            operation = scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        val names = uris.map { uri ->
                            val filename = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
                                .orEmpty()
                            uri to filename
                        }
                        val job = currentCoroutineContext()
                        val zip = names.singleOrNull()?.takeIf { it.second.endsWith(".zip", true) }
                        if (zip != null) {
                            context.contentResolver.openInputStream(zip.first)?.use { rvcStore.installZip(it) { job.ensureActive() } } ?: error("No se pudo abrir ZIP")
                        } else {
                            val model = names.singleOrNull { it.second.endsWith(".pth", true) }
                            val index = names.singleOrNull { it.second.endsWith(".index", true) }
                            if (model != null && names.size <= 2) {
                                context.contentResolver.openInputStream(model.first)?.use { input ->
                                    if (index != null) context.contentResolver.openInputStream(index.first)?.use { idx ->
                                        rvcStore.install(model.second, input, idx) { job.ensureActive() }
                                    } ?: error("No se pudo abrir índice")
                                    else rvcStore.install(model.second, input) { job.ensureActive() }
                                } ?: error("No se pudo abrir modelo")
                            } else if (index != null && names.size == 1 && selectedRvcIndex != null) {
                                context.contentResolver.openInputStream(index.first)?.use { rvcStore.attachIndex(requireNotNull(selectedRvcIndex), it) { job.ensureActive() } }
                                    ?: error("No se pudo abrir índice")
                            } else error("Selecciona un ZIP RVC, un .pth con su .index opcional, o un .index para un modelo existente.")
                        }
                    }
                    rvcModels = rvcStore.list()
                    report("Modelo RVC importado y almacenado como datos. No puede reproducirse sin un motor de conversión compatible.")
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { report("Importación RVC rechazada: revisa formato, nombre, tamaño, estructura, autorización o almacenamiento.") }
                finally { busy = false }
            }
        }
    }
    val importRvcOnnx = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            stop()
            operation = scope.launch {
                busy = true
                try {
                    withContext(Dispatchers.IO) {
                        require(uris.size == 3) { "Selecciona exactamente tres ONNX." }
                        val sources = uris.map { uri ->
                            val filename = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                                ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }.orEmpty()
                            RvcOnnxNames.kind(filename) to uri
                        }
                        require(sources.map { it.first }.toSet() == setOf("synth", "hubert", "rmvpe")) {
                            "Selecciona un sintetizador ONNX, un HuBERT ONNX y un RMVPE ONNX."
                        }
                        val streams = sources.associate { (key, uri) ->
                            key to (context.contentResolver.openInputStream(uri)
                                ?: throw IllegalArgumentException("No se pudo abrir un ONNX."))
                        }
                        val job = currentCoroutineContext()
                        rvcOnnx.install(streams) { job.ensureActive() }
                    }
                    rvcOnnxReady = rvcOnnx.ready()
                    rvcOnnxEnabled = false
                    report("Tres modelos ONNX importados. Activa RVC local para probar la conversión; no se necesita Internet.")
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { report("No se pudo instalar RVC ONNX: comprueba los tres archivos, sus nombres y el espacio disponible.") }
                finally { busy = false }
            }
        }
    }
    Text("Biblioteca de voces", style = MaterialTheme.typography.headlineMedium)
    if (HostedSettings(context).provider == "groq") Text("GroqCloud Free: para reproducir respuestas elige Android TTS o Qwen3-TTS experimental. Las voces OpenAI y la API externa se conservan para otros modos; no se utilizan ni se facturan desde la conversación gratuita.")
    if (!compact) ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Tu selección", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Text(if (profile.ttsEngine == "realtime") profile.voice else profile.ttsVoice.ifBlank { "Elige una voz" }, style = MaterialTheme.typography.headlineSmall)
            Text(VoiceSynthesisContract.label(profile.ttsEngine))
            Text("La selección no reproduce audio. Escucha una muestra explícita antes de guardar.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { stop(); preview(profile) }, enabled = !busy && runCatching { profile.validated() }.isSuccess) { Text("Reproducir muestra seleccionada") }
        }
    }
    Text("Las voces son síntesis de IA. Utiliza voces originales o autorizadas. No se incluyen modelos ni clonaciones de actores. Una importación válida no garantiza inferencia ni calidad.")
    Text("RVC · voces entrenadas", style = MaterialTheme.typography.titleLarge)
    Text("Los .pth y .index se pueden importar como archivo original. Para convertir voz en el teléfono se necesitan tres modelos ONNX compatibles: sintetizador RVC (exportado desde .pth en el ordenador), HuBERT/ContentVec y RMVPE.")
    Row { Checkbox(rights, { rights = it }); Text("He revisado los derechos y la procedencia de la voz") }
    Button(onClick = { importRvc.launch(arrayOf("*/*")) }, enabled = rights && !busy) { Text("Importar modelo RVC (.zip, .pth, .index)") }
    Button(onClick = { importRvcOnnx.launch(arrayOf("*/*")) }, enabled = rights && !busy) {
        Text("Instalar motor RVC local (.onnx × 3)")
    }
    Text(if (rvcOnnxReady) "Modelos ONNX disponibles en el teléfono."
        else "RVC local todavía no configurado: faltan los tres modelos ONNX compatibles.")
    Row {
        Checkbox(checked = rvcOnnxEnabled, onCheckedChange = { value ->
            stop()
            try {
                rvcOnnx.setEnabled(value)
                rvcOnnxEnabled = value
            } catch (_: Exception) { report("Instala primero los tres ONNX. Esta función solo usa modelos en el teléfono.") }
        }, enabled = !busy && (rvcOnnxEnabled || rvcOnnxReady && rights))
        Text("RVC local sobre Piper, Kokoro o Android TTS")
    }
    Text("El motor ONNX no sube audio ni pesos a Internet; puede consumir mucha memoria y ser lento. No todas las exportaciones RVC son compatibles. Desactívalo si notas cortes.")
    if (rvcModels.isEmpty()) Text("No hay voces RVC importadas.")
    rvcModels.forEach { model ->
        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(model.name, style = MaterialTheme.typography.titleMedium)
                Text("Modelo: ${model.modelBytes / 1048576} MiB · índice: ${if (model.indexBytes > 0) "presente" else "opcional, sin importar"}")
                Text("Importado · inferencia no disponible. No es una voz lista para seleccionar ni previsualizar.")
                if (model.indexBytes == 0L) TextButton(onClick = {
                    selectedRvcIndex = model.id
                    importRvc.launch(arrayOf("*/*"))
                }, enabled = !busy && rights) { Text("Añadir archivo .index") }
                TextButton(onClick = { operation = scope.launch {
                    busy = true
                    try { withContext(Dispatchers.IO) { rvcStore.delete(model.id) }; rvcModels = rvcStore.list() }
                    catch (_: Exception) { report("No se pudo eliminar el modelo RVC.") }
                    finally { busy = false }
                } }, enabled = !busy) { Text("Eliminar modelo") }
            }
        }
    }
    val engines = listOf("qwen_space" to "Qwen3-TTS online · cola pública", "realtime" to "OpenAI Realtime · audio nativo", "openai_tts" to "OpenAI Speech · transitorio hasta 06/01/2027", "external" to "API TTS externa · contrato PCM", "sherpa_vits" to "Modelo Piper/VITS · Sherpa local")
    if (compact) Box {
        OutlinedButton(onClick = { engineMenu = true }) { Text("Motor: ${VoiceSynthesisContract.label(profile.ttsEngine)}") }
        DropdownMenu(expanded = engineMenu, onDismissRequest = { engineMenu = false }) {
            engines.forEach { (id, title) -> DropdownMenuItem(text = { Text(title) },
                enabled = id != "openai_tts" || VoiceSynthesisContract.speechAvailable(),
                onClick = { stop(); change(VoiceSelection.engine(profile, id)); engineMenu = false }) }
        }
    } else engines.forEach { (id, title) ->
        FilterChip(selected = profile.ttsEngine == id, enabled = id != "openai_tts" || VoiceSynthesisContract.speechAvailable(), onClick = { stop(); change(VoiceSelection.engine(profile, id)) }, label = { Text(title) })
    }
    Text("Motor del perfil: ${VoiceSynthesisContract.label(profile.ttsEngine)}. Guarda y abre una sesión nueva para aplicar los cambios.")
    if (profile.ttsEngine == "qwen_space") QwenVoicePanel(context, profile, change, stop, preview)
    if (profile.ttsEngine in listOf("realtime", "openai_tts")) {
        Text("Voces oficiales")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VoiceSelection.catalog(profile.ttsEngine).forEach { id -> FilterChip(selected = profile.voice == id, onClick = {
                stop()
                android.util.Log.i("PbtVoice", "OFFICIAL_VOICE_SELECTED")
                change(VoiceSelection.official(profile, id))
            }, label = { Text(id) }) }
        }
        if (profile.ttsEngine == "openai_tts") Text("Este endpoint está anunciado como obsoleto y requiere tu clave BYOK de OpenAI, incluso si la conversación usa servidor. La muestra y la síntesis consumen cuota adicional. Usa Realtime para evitar esta ruta transitoria.")
        if (advanced) OutlinedTextField(profile.tone, { change(profile.copy(tone = it.take(4000))) }, label = { Text("Instrucciones de tono y expresión") }, modifier = Modifier.fillMaxWidth())
    }
    if (profile.ttsEngine == "android" && profile.voiceMode == "rapida") {
        Text("Voces offline instaladas. Para una voz española más natural prueba un motor neuronal gratuito como HayaiTTS o Kokoly y descarga dentro de él una voz española. Se integra mediante la API Android TTS, sin servidor ni pagos por síntesis; calidad y velocidad dependen del modelo y del teléfono.")
        OutlinedButton(onClick = {
            runCatching { context.startActivity(android.content.Intent("com.android.settings.TTS_SETTINGS").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { report("Abre Ajustes de Android → Texto a voz para instalar y elegir un motor neuronal.") }
        }) { Text("Configurar motor de voz en Android") }
        Button(onClick = { stop(); operation = scope.launch {
            busy = true
            try { voices = AndroidVoiceEngine(context).voices(); if (voices.isEmpty()) report("No se han encontrado voces locales instaladas. Instala un motor y su idioma en los ajustes de síntesis de Android.") }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { report("No se pudieron consultar los motores TTS instalados.") }
            finally { busy = false }
        } }, enabled = !busy) { Text("Consultar voces locales instaladas") }
        voices.sortedWith(compareByDescending<AndroidVoice> { it.language.equals("es-ES", true) }
            .thenByDescending { it.engine.contains("hayai", true) || it.engine.contains("kokoly", true) }).forEach { voice -> FilterChip(selected = profile.ttsVoice == voice.name && profile.ttsPackage == voice.engine,
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
        OutlinedButton(onClick = { stop(); runCatching { settings.revoke() }.onSuccess { externalConsent = false; report("Autorización revocada. No se enviarán nuevas respuestas al proveedor hasta volver a autorizarlo.") }.onFailure { report("No se pudo revocar la autorización local.") } }) { Text("Desactivar proveedor externo") }
        Text("La conexión al proveedor se configura por teléfono; modelo y voz se guardan por asistente. Velocidad requiere que el proveedor admita el campo speed. No hay controles acústicos adicionales sin contrato implementado.")
    }
    if (profile.ttsEngine == "sherpa_vits") {
        Text("Solo paquetes ZIP sherpa-piper-phonemes-v1 con modelo, símbolos, pronunciaciones y manifiesto. No se ejecutan instaladores ni bibliotecas del paquete. El diccionario debe cubrir cada palabra; no se incluye un fonemizador universal.")
        Row { Checkbox(rights, { rights = it }); Text("Dispongo de autorización y he revisado licencia y procedencia del modelo") }
        Button(onClick = { import.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) }, enabled = rights && !busy) { Text("Importar paquete de voz compatible") }
        if (packages.isEmpty()) Text("No hay modelos instalados. Consulta docs/BIBLIOTECA_DE_VOCES.md para preparar el paquete; un ONNX aislado no es suficiente.")
        packages.forEach { pack -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val compatible = runCatching { LocalModelClient(context).checkHardware(pack) }.isSuccess
            Text(pack.name, style = MaterialTheme.typography.titleMedium)
            Text("${pack.format} · ${pack.engine} · ${pack.language}")
            Text("RAM declarada: ${pack.minRamMb} MiB · modelo: ${pack.bytes / 1048576} MiB · CPU arm64-v8a/x86_64")
            Text("Licencia: ${pack.license}\nProcedencia: ${pack.source}")
            Text("Estructura validada. La inferencia y la escucha son comprobaciones separadas y dependen del dispositivo.")
            Text(if (compatible) "El dispositivo cumple los requisitos declarados; no acredita rendimiento ni calidad." else "El dispositivo no cumple los requisitos declarados o Android informa de presión de memoria.")
            Button(onClick = { change(profile.copy(ttsVoice = pack.id, language = pack.language)) }) { Text(if (profile.ttsVoice == pack.id) "Modelo seleccionado" else "Seleccionar modelo") }
            OutlinedButton(onClick = { stop(); preview(profile.copy(ttsEngine = "sherpa_vits", ttsVoice = pack.id)) }, enabled = !busy && compatible && profile.name.isNotBlank()) { Text("Probar inferencia y muestra de este modelo") }
            TextButton(onClick = { stop(); operation = scope.launch { busy = true; try { withContext(Dispatchers.IO) { store.delete(pack.id) }; reload(); report("Modelo eliminado y almacenamiento liberado. Los perfiles que lo referencian necesitan elegir otra voz.") } catch (_: Exception) { report("No se pudo eliminar el modelo.") } finally { busy = false } } }, enabled = !busy) { Text("Eliminar modelo y liberar espacio") }
        } } }
    }
    TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar ajustes avanzados de voz" else "Ajustes avanzados de voz") }
    if (advanced) {
    if (profile.ttsEngine != "qwen_space") {
    Text("Velocidad: ${"%.2f".format(java.util.Locale.ROOT, profile.speed)}×")
    Slider(profile.speed.toFloat(), { change(profile.copy(speed = it.toDouble())) }, valueRange = 0.25f..1.5f)
    }
    OutlinedTextField(profile.language, { change(profile.copy(language = it.take(30))) }, label = { Text("Idioma de conversación del perfil") }, modifier = Modifier.fillMaxWidth())
    if (profile.ttsEngine in listOf("android", "sherpa_vits", "external")) Text("Cambiar el idioma de conversación no reentrena la voz: la pronunciación depende del idioma y vocabulario del motor/modelo elegido.")
    }
    Button(onClick = { stop(); save() }, enabled = !busy) { Text("Guardar motor y voz en el asistente") }
    Button(onClick = { stop(); preview(profile) }, enabled = !busy && profile.name.isNotBlank()) { Text("Escuchar muestra (puede consumir API)") }
    OutlinedButton(onClick = { operation?.cancel(); stop() }) { Text("Cancelar operación y audio pendiente") }
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    if (!compact || advanced) Text("La conversación alternativa espera el texto y la síntesis antes de reproducir. Durante la reproducción se apaga su micrófono para evitar realimentación; puedes interrumpir con el botón. No se promete la ruta de las gafas sin probarla.")
}
