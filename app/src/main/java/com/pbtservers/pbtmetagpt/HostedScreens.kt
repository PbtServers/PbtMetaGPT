package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.*

@Composable fun HostedProviderScreen(context: Context, provider: String, select: (String) -> Unit, openOpenAI: () -> Unit, compact: Boolean = false) {
    var advanced by remember { mutableStateOf(!compact) }
    val settings = remember { HostedSettings(context) }
    val vault = remember { KeyVault(context, provider = "groq") }
    val scope = rememberCoroutineScope()
    var key by remember { mutableStateOf("") }
    var present by remember { mutableStateOf(vault.exists()) }
    var model by remember { mutableStateOf(settings.model) }
    var consent by remember { mutableStateOf(settings.authorized) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Conexión y generación reales pendientes de tu clave y cuota.") }
    var operation by remember { mutableStateOf<Job?>(null) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { key = ""; operation?.cancel() } }
        owner.lifecycle.addObserver(observer)
        onDispose { key = ""; operation?.cancel(); owner.lifecycle.removeObserver(observer) }
    }
    Text("Elige quién responde. No necesitas administrar servidores ni ejecutar modelos grandes en el teléfono.")
    FilterChip(selected = provider == "groq", onClick = { select("groq") }, label = { Text("GroqCloud · plan Free con límites") })
    FilterChip(selected = provider == "openai", onClick = { select("openai") }, label = { Text("OpenAI API · opcional y facturable") })
    if (provider == "openai") {
        Text("Has elegido OpenAI API. Requiere facturación independiente de ChatGPT; no es el modo gratuito.")
        OutlinedButton(onClick = openOpenAI) { Text("Configurar OpenAI opcional") }
    } else {
        Text("Crea una cuenta gratuita en console.groq.com y genera una clave en console.groq.com/keys. El plan Free no exige tarjeta. No actives Developer para este modo.")
        Text("El servicio y el modelo se identifican como GroqCloud. No es ChatGPT. Una clave de una cuenta de pago puede generar cargos: la app no puede verificar el plan desde la clave.")
        if (advanced) Text("Las cuotas dependen del modelo y la cuenta; un HTTP 429 detiene el turno. No hay reintentos automáticos ni cambio a OpenAI. Consulta console.groq.com/settings/limits.")
        Text(if (present) "Clave Groq cifrada guardada en Android Keystore." else "No hay clave Groq guardada.")
        OutlinedTextField(key, { key = it.take(220) }, label = { Text("Clave gratuita personal de GroqCloud") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            try { vault.save(key.trim()); key = ""; present = true; status = "Clave guardada cifrada. Comprueba acceso y generación." }
            catch (_: Exception) { status = "No se pudo guardar la clave. Revisa su formato y Keystore." }
        }, enabled = key.isNotBlank() && !busy) { Text("Guardar clave Groq cifrada") }
        OutlinedButton(onClick = {
            operation?.cancel()
            try { vault.delete(); settings.saveGroq(model, false); present = false; consent = false; status = "Clave y autorización Groq eliminadas." }
            catch (_: Exception) { status = "No se pudo eliminar la configuración." }
        }, enabled = present && !busy) { Text("Eliminar clave Groq y autorización") }
        if (advanced) OutlinedTextField(model, { model = it.take(120) }, label = { Text("Modelo Groq (ID público)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row { Checkbox(consent, { consent = it }); Text("Confirmo que uso Groq Free y autorizo enviar las peticiones, el contexto y las instrucciones del asistente a GroqCloud. El audio Vosk permanece local.") }
        Button(onClick = {
            try { settings.saveGroq(model.trim(), consent); status = "Modelo y consentimiento guardados. No se ha enviado ninguna conversación." }
            catch (_: Exception) { status = "ID de modelo no válido o error de almacenamiento." }
        }, enabled = !busy) { Text("Guardar servicio gratuito") }
        fun runProbe(generation: Boolean) {
            operation = scope.launch {
                busy = true
                val started = android.os.SystemClock.elapsedRealtime()
                try {
                    settings.saveGroq(model.trim(), consent)
                    val api = GroqApi()
                    val models = api.models(vault.read())
                    if (generation) {
                        check(consent)
                        require(model.trim() in models)
                        val result = api.answer(vault.read(), model.trim(), listOf("system" to "Responde brevemente en español.", "user" to "Di hola en una frase."))
                        status = "Generación REAL GroqCloud · ${model.trim()} · ${android.os.SystemClock.elapsedRealtime() - started} ms:\n$result"
                    } else status = "Acceso REAL correcto · ${android.os.SystemClock.elapsedRealtime() - started} ms. IDs disponibles (también incluye modelos de audio):\n${models.joinToString("\n")}. Esta prueba no valida generación."
                } catch (cancel: CancellationException) { throw cancel }
                catch (problem: HostedFailure) { status = problem.publicMessage }
                catch (_: Exception) { status = "No se completó la prueba. Revisa modelo, consentimiento y clave." }
                finally { busy = false }
            }
        }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Cerrar detalles del servicio" else "Detalles del servicio y modelos") }
        if (advanced) OutlinedButton(onClick = { runProbe(false) }, enabled = present && !busy) { Text("Comprobar conexión y modelos reales") }
        Button(onClick = { runProbe(true) }, enabled = present && consent && !busy) { Text("Probar respuesta real (consume cuota gratuita)") }
        if (busy) OutlinedButton(onClick = { operation?.cancel(); status = "Prueba cancelada. El proveedor puede haber consumido cuota." }) { Text("Cancelar prueba") }
        Text(status)
        if (advanced) Text("Si este modelo no está disponible, consulta y selecciona otro ID autorizado de Groq. Gemini API no se ofrece como alternativa gratuita para usuarios del EEE por sus condiciones. Consulta docs/SERVICIOS_ONLINE.md; OpenAI solo se utiliza por elección explícita.")
    }
}

@Composable fun HostedConversationScreen(context: Context, profile: AssistantProfile?, microphoneReady: Boolean,
    requestMic: () -> Unit, stop: () -> Unit, chooseVoice: () -> Unit = {}, configure: () -> Unit) {
    val voice = (context.applicationContext as PbtApplication).hosted
    val state by voice.state.collectAsStateWithLifecycle()
    val busy by voice.busy.collectAsStateWithLifecycle()
    val messages by voice.messages.collectAsStateWithLifecycle()
    val phase by voice.phase.collectAsStateWithLifecycle()
    val engine by voice.engine.collectAsStateWithLifecycle()
    val audioError by voice.audioError.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }
    var fallback by remember { mutableStateOf(false) }
    var localVoices by remember { mutableStateOf(emptyList<AndroidVoice>()) }
    var localStatus by remember { mutableStateOf("") }
    val list = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(messages.lastOrNull()?.text) { if (messages.isNotEmpty()) list.animateScrollToItem(messages.lastIndex) }
    LaunchedEffect(fallback) {
        if (fallback) try {
            localVoices = AndroidVoiceEngine(context).voices().filter { it.language.substringBefore('-') == profile?.language?.substringBefore('-') }
            if (localVoices.isEmpty()) localStatus = "No hay voces locales instaladas para este idioma."
        } catch (cancel: CancellationException) { throw cancel }
        catch (_: Exception) { localStatus = "No se pudo consultar las voces instaladas." }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(profile?.name ?: "Conversación", style = MaterialTheme.typography.headlineMedium)
        Text("GroqCloud · ${phase.label}", style = MaterialTheme.typography.labelLarge)
        Text(engine, style = MaterialTheme.typography.bodySmall)
        androidx.compose.foundation.lazy.LazyColumn(state = list, modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(messages.size, key = { messages[it].id }) { index ->
                val message = messages[index]
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.assistant) Arrangement.Start else Arrangement.End) {
                    Surface(shape = MaterialTheme.shapes.large, color = if (message.assistant) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(0.92f)) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (message.assistant) "GroqCloud" else "Tú", style = MaterialTheme.typography.labelMedium)
                            androidx.compose.foundation.text.selection.SelectionContainer { Text(message.text.ifBlank { "…" }) }
                        }
                    }
                }
            }
        }
        if (state.failed) {
            Text(state.status, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = configure) { Text("Revisar conexión") }
        }
        if (audioError != null) {
            Text(audioError.orEmpty(), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Row {
                TextButton(onClick = { voice.replay() }, enabled = !busy) { Text("Reintentar voz") }
                TextButton(onClick = chooseVoice) { Text("Cambiar voz") }
                TextButton(onClick = { fallback = true }) { Text("Usar voz Android…") }
            }
        }
        if (messages.any { it.assistant && it.text.isNotBlank() } && audioError == null)
            TextButton(onClick = { voice.replay() }, enabled = !busy) { Text("Escuchar de nuevo") }
        OutlinedTextField(input, { input = it.take(2000) }, label = { Text("Escribe un mensaje") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val ready = !busy
            Button(onClick = {
                if (!state.active) profile?.let { voice.start(it) }
                voice.send(input); input = ""
            }, enabled = ready && profile != null && input.isNotBlank()) { Text("Enviar") }
            OutlinedButton(onClick = {
                if (!microphoneReady) requestMic()
                else { if (!state.active) profile?.let { voice.start(it) }; voice.listen() }
            }, enabled = ready && profile != null) { Text("Micrófono") }
            OutlinedButton(onClick = stop) { Text("Detener") }
        }
    }
    if (fallback) AlertDialog(onDismissRequest = { fallback = false }, title = { Text("Elegir voz local") }, text = {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
            Text("Solo se usará para repetir esta respuesta. No se enviará a otro servicio.")
            if (localStatus.isNotBlank()) Text(localStatus)
            localVoices.forEachIndexed { index, local -> TextButton(onClick = {
                profile?.let { voice.replay(it.copy(voiceMode = "rapida", ttsEngine = "android", ttsVoice = local.name, ttsPackage = local.engine)) }; fallback = false
            }) { Text("Voz ${index + 1} · ${local.language}") } }
        }
    }, confirmButton = { TextButton(onClick = { fallback = false }) { Text("Cancelar") } })
}

@Composable fun QwenVoicePanel(context: Context, profile: AssistantProfile, change: (AssistantProfile) -> Unit,
    stop: () -> Unit, preview: (AssistantProfile) -> Unit) {
    val settings = remember { HostedSettings(context) }
    val scope = rememberCoroutineScope()
    var authorized by remember { mutableStateOf(settings.qwenAuthorized) }
    var status by remember { mutableStateOf("Space público experimental · comprueba su disponibilidad antes de utilizarlo.") }
    var busy by remember { mutableStateOf(false) }
    Text("Qwen3-TTS · voces neuronales online", style = MaterialTheme.typography.titleLarge)
    val previewState by (context.applicationContext as PbtApplication).voice.state.collectAsStateWithLifecycle()
    if (previewState.connecting || previewState.responding || previewState.active) Text(previewState.status)
    Text("Proveedor: Space público oficial Qwen/Qwen3-TTS en Hugging Face ZeroGPU. Sin despliegue propio. Puede estar suspendido, cambiar de API o agotar su cuota compartida. No ofrece disponibilidad garantizada.")
    Text("CustomVoice: 0.6B / 1.7B; nueve voces. Español compatible; catalán no documentado. Sin VoiceDesign ni VoiceClone en este adaptador.")
    Text(status)
    Row { Checkbox(authorized, {
        stop()
        try { settings.authorizeQwen(it); authorized = it }
        catch (_: Exception) { status = "No se pudo guardar el consentimiento." }
    }); Text("Autorizo transmitir el texto de respuestas y muestras a este Space público para sintetizar voz. He revisado sus condiciones. Sin token HF: cuota anónima, sin créditos de pago.") }
    Button(onClick = { scope.launch {
        busy = true
        try { HostedHttp.qwenApi.probe(); status = "Contrato remoto compatible ahora. La generación y reproducción aún requieren una muestra real." }
        catch (cancel: CancellationException) { throw cancel }
        catch (problem: HostedFailure) { status = problem.publicMessage }
        catch (_: Exception) { status = "Space incompatible o no disponible. No se ha enviado texto." }
        finally { busy = false }
    } }, enabled = !busy) { Text("Comprobar API pública sin enviar texto") }
    QwenSpaceContract.sizes.forEach { size -> FilterChip(selected = profile.ttsPackage == size, onClick = { stop(); change(profile.copy(ttsPackage = size)) }, label = { Text("CustomVoice $size") }) }
    QwenSpaceContract.speakers.forEach { speaker -> FilterChip(selected = profile.ttsVoice == speaker, onClick = { stop(); change(profile.copy(ttsVoice = speaker)) }, label = { Text(speaker) }) }
    OutlinedButton(onClick = { stop(); preview(profile) }, enabled = authorized && !busy && profile.ttsVoice in QwenSpaceContract.speakers && profile.ttsPackage in QwenSpaceContract.sizes && profile.name.isNotBlank()) { Text("Probar síntesis remota experimental") }
    Text("La muestra consume cuota. Si no funciona, elige Android TTS; no se sustituye automáticamente. Cola y generación cancelables; el servidor puede terminar una petición ya enviada.")
}
