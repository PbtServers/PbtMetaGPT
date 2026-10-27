package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID

@Composable
fun AssistantShell(context: Context, vault: KeyVault, voice: RealtimeClient, wake: LocalWakeListener,
    microphoneReady: Boolean, requestMic: () -> Unit, stopAll: () -> Unit,
    report: (String) -> Unit, bindWakeStart: ((AssistantProfile) -> Unit) -> Unit,
    legacy: @Composable (page: String, configured: Boolean, backend: String, token: String, bluetooth: Boolean, setBluetooth: (Boolean) -> Unit, start: () -> Unit) -> Unit,
) {
    val store = remember { ProfileStore(context) }
    val preferences = remember { context.getSharedPreferences("configuracion", Context.MODE_PRIVATE) }
    var profiles by remember { mutableStateOf(runCatching { store.load() }.getOrElse { report("No se pudieron leer los perfiles. Importa una copia o crea otro."); emptyList() }) }
    var selectedId by remember { mutableStateOf(store.selected()) }
    val selected = profiles.find { it.id == selectedId }
    var draft by remember { mutableStateOf(selected ?: AssistantProfile()) }
    var page by remember { mutableStateOf(if (preferences.getBoolean("bienvenida", false)) "Inicio" else "Bienvenida") }
    var wizard by remember { mutableStateOf(!preferences.getBoolean("bienvenida", false)) }
    var step by remember { mutableIntStateOf(0) }
    val steps = listOf("Bienvenida", "Privacidad", "OpenAI", "Comprobar conexión", "Asistentes", "Nombre del asistente", "Activación por voz", "Voz", "Personalidad", "Permisos", "Dispositivos", "Conversación")
    var direct by remember { mutableStateOf(preferences.getBoolean("directo", true)) }
    var keyPresent by remember { mutableStateOf(vault.exists()) }
    var keyInput by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(preferences.getString("backend", "").orEmpty()) }
    var token by remember { mutableStateOf("") }
    var bluetooth by remember { mutableStateOf(false) }
    var consent by remember { mutableStateOf(false) }
    var allProfiles by remember { mutableStateOf(false) }
    var returnToWake by remember { mutableStateOf(false) }
    var hadSession by remember { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) { returnToWake = false; consent = false; hadSession = false; keyInput = ""; token = "" }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); wake.stop() }
    }
    val vs by voice.state.collectAsStateWithLifecycle()
    val wakeStatus by wake.status.collectAsStateWithLifecycle()
    var exportText by remember { mutableStateOf("") }
    fun save(list: List<AssistantProfile>, id: String?) {
        store.save(list, id); profiles = list; selectedId = id
    }
    fun configure(requireProfile: Boolean = true, target: AssistantProfile? = selected): Boolean {
        if (target == null && requireProfile) { report("Crea y guarda un asistente antes de iniciar OpenAI."); return false }
        if (voice.state.value.active || voice.state.value.connecting) { report("Finaliza la sesión antes de cambiar la configuración."); return false }
        voice.configure(target, if (direct) ({ vault.read() }) else null)
        preferences.edit().putBoolean("directo", direct).putString("backend", backend).apply()
        return true
    }
    val configured = selected != null && if (direct) keyPresent else backend.isNotBlank() && token.isNotBlank()
    val start: () -> Unit = {
        wake.stop()
        if (!microphoneReady) requestMic()
        else if (configure()) voice.start(backend, token.trim(), bluetooth)
    }
    LaunchedEffect(vs.active, vs.connecting) {
        if (returnToWake && (vs.active || vs.connecting)) hadSession = true
        if (returnToWake && hadSession && !vs.active && !vs.connecting) {
            hadSession = false
            if (consent && owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) selected?.let { wake.start(if (allProfiles) listOf(it) + profiles.filter { p -> p.id != it.id } else listOf(it)) }
        }
    }
    SideEffect { bindWakeStart { matched ->
        if (matched.confirmationSound) runCatching {
            val tone = android.media.ToneGenerator(android.media.AudioManager.STREAM_NOTIFICATION, 45)
            tone.startTone(android.media.ToneGenerator.TONE_PROP_ACK, 120)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ tone.release() }, 200)
        }
        runCatching { save(profiles, matched.id); draft = matched }.onFailure { report("No se pudo seleccionar el asistente activado.") }
        returnToWake = true; page = "Conversación"; wake.stop()
        if (!microphoneReady) requestMic() else if (configure(target = matched)) voice.start(backend, token.trim(), bluetooth)
    } }
    // Solo al entrar en diagnóstico; sus pruebas usan la misma selección explícita.
    LaunchedEffect(page) { if (page == "Diagnóstico" && !vs.active && !vs.connecting) configure() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(exportText.toByteArray(Charsets.UTF_8)) } ?: error("Sin archivo") }
            .onSuccess { report("Perfiles exportados. El archivo contiene instrucciones personales; no incluye el almacén de claves.") }
            .onFailure { report("No se pudo exportar el archivo de perfiles.") }
        exportText = ""
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use { input ->
                val output = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (output.size() <= 262144) {
                    val count = input.read(buffer, 0, minOf(buffer.size, 262145 - output.size()))
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
                val bytes = output.toByteArray()
                require(bytes.size <= 262144) { "Archivo demasiado grande" }; bytes.toString(Charsets.UTF_8)
            } ?: error("Sin archivo")
            val incoming = AssistantProfile.import(text).map { it.copy(id = UUID.randomUUID().toString()) }
            save(profiles + incoming, incoming.first().id); draft = incoming.first()
        }.onSuccess { report("Perfiles importados. Revisa sus instrucciones antes de utilizarlos.") }
            .onFailure { report("Importación rechazada: formato, tamaño o campos no permitidos. No se admiten credenciales.") }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
        Text("PbtMetaGPT · ${BuildConfig.VERSION_NAME} · experimental", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
        if (wizard) {
            Text("Configuración inicial · ${step + 1}/${steps.size} · ${steps[step]}", modifier = Modifier.padding(horizontal = 16.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { if (step > 0) step--; page = steps[step]; stopAll() }, enabled = step > 0) { Text("Anterior") }
                Button(onClick = {
                    stopAll()
                    if (step < steps.lastIndex) { step++; page = steps[step] }
                    else if (configured) { wizard = false; page = "Inicio"; preferences.edit().putBoolean("bienvenida", true).apply() }
                    else report("Guarda una clave o configura el servidor y crea tu asistente antes de terminar.")
                }) { Text(if (step == steps.lastIndex) "Terminar" else "Siguiente") }
            }
        } else Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Inicio", "Conversación", "Asistentes", "Personalidad", "Voz", "Activación por voz", "OpenAI", "Dispositivos", "Diagnóstico", "Privacidad").forEach { destination ->
                FilterChip(selected = page == destination, onClick = {
                    returnToWake = false; consent = false
                    if (destination in listOf("Inicio", "Conversación", "Dispositivos")) wake.stop() else stopAll()
                    page = destination; if (destination in listOf("Personalidad", "Voz", "Activación por voz")) draft = selected ?: AssistantProfile()
                }, label = { Text(destination) })
            }
        }
        Text(wakeStatus, modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.primary)
        selected?.let { Text("${it.name} · ${it.voice} · ${it.phrase}", modifier = Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelLarge) }
        if (vs.active || vs.connecting || wake.armed) {
            Text(vs.status, modifier = Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = { returnToWake = false; consent = false; stopAll() }) { Text("Desactivar escucha y cerrar sesión") }
        }
        if (page in listOf("Inicio", "Conversación", "Dispositivos", "Diagnóstico")) {
            legacy(page, configured, backend, token, bluetooth, { bluetooth = it }, start)
        } else Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(page, style = MaterialTheme.typography.headlineMedium)
            when (page) {
                "Bienvenida" -> {
                    Text("Tu asistente, con tu nombre y tu estilo. Conversa desde el móvil o prueba las Ray-Ban Meta. No necesitas servidor en el modo directo.")
                    Text("Las gafas, el audio y el reconocimiento local necesitan validación en tu dispositivo. Puedes usar siempre el botón manual.")
                }
                "Privacidad" -> {
                    Text("BYOK: introduces tu propia clave API. Se cifra con AES-GCM y una clave de Android Keystore; no se incluye en la APK ni en perfiles exportados. Una clave permanente en un móvil tiene riesgo: un proceso comprometido, dispositivo rooteado o depuración puede exponerla mientras se usa. El cifrado no elimina ese riesgo.")
                    Text("Utiliza una clave de proyecto con permisos y presupuesto reducidos. Vigila el consumo y revócala si pierdes el teléfono. El backend opcional evita guardar esa clave en el cliente y añade límites de uso.")
                    Text("ChatGPT Plus y otros planes de ChatGPT no incluyen la facturación de OpenAI API. La voz y las muestras consumen cuota de API.")
                    Text("La conversación autorizada y las fotos confirmadas se envían a OpenAI. El detector local no envía ni guarda su audio. Solo funciona con la app visible; se desactiva al salir o navegar. Los perfiles contienen información personal y se exportan sin cifrar.")
                    Text("No se clonan voces ni se garantiza una interpretación exacta de personajes. Las instrucciones de tono son preferencias, no controles acústicos exactos.")
                }
                "OpenAI", "Comprobar conexión" -> {
                    Toggle("Conexión directa con tu clave (sin backend)", direct) { stopAll(); direct = it; preferences.edit().putBoolean("directo", it).apply() }
                    if (direct) {
                        Text("Riesgo BYOK: la clave permanente se utilizará en este teléfono. Keystore protege el almacenamiento, pero no un proceso comprometido. No compartas tu clave.")
                        Text(if (keyPresent) "Hay una clave cifrada guardada. No se muestra su contenido." else "No hay clave guardada.")
                        OutlinedTextField(keyInput, { if (it.length <= 504) keyInput = it }, label = { Text("Clave API personal de OpenAI") }, visualTransformation = PasswordVisualTransformation(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), modifier = Modifier.fillMaxWidth())
                        Button(onClick = {
                            stopAll()
                            runCatching { vault.save(keyInput.trim()) }.onSuccess { keyInput = ""; keyPresent = true; report("Clave cifrada guardada. Comprueba la conexión antes de conversar.") }
                                .onFailure { report("No se pudo guardar la clave. Comprueba su formato y la disponibilidad de Android Keystore.") }
                        }, enabled = keyInput.isNotBlank()) { Text(if (keyPresent) "Sustituir clave cifrada" else "Guardar clave cifrada") }
                        OutlinedButton(onClick = { stopAll(); runCatching { vault.delete() }.onSuccess { keyPresent = false; keyInput = ""; report("Clave eliminada y sesiones cerradas.") }.onFailure { report("No se pudo eliminar la clave. Revisa el dispositivo.") } }, enabled = keyPresent) { Text("Eliminar clave") }
                        Text("Obtén la clave en platform.openai.com/api-keys. No utilices la contraseña de ChatGPT. Activa facturación de API y revisa permisos y límites de tu proyecto.")
                    } else {
                        Text("Alternativa con servidor: la clave OpenAI permanece en tu backend HTTPS. El token personal del backend se conserva solo en memoria.")
                        Field("Dirección HTTPS del backend", backend) { backend = it }
                        OutlinedTextField(token, { if (it.length <= 256) token = it }, label = { Text("Token personal del backend") }, visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    Button(onClick = { wake.stop(); if (configure(false)) voice.probeCredentials(backend, token.trim()) }, enabled = (if (direct) keyPresent else backend.isNotBlank() && token.isNotBlank()) && !vs.active && !vs.connecting) { Text("Comprobar credenciales sin audio") }
                    Text(vs.status)
                    Text("La comprobación solicita una credencial temporal; no acredita micrófono, altavoz ni gafas. Guarda un asistente antes de conversar.")
                }
                "Asistentes" -> {
                    Text("No hay un personaje obligatorio. Crea el tuyo o elige una inspiración opcional.")
                    listOf("Personalizado", "Jarvis", "Friday", "PbtAdmin").forEach { name ->
                        OutlinedButton(onClick = { stopAll(); draft = AssistantProfile.template(name); page = "Personalidad" }) { Text("Crear $name") }
                    }
                    profiles.forEach { profile ->
                        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(profile.name + if (profile.id == selectedId) " · seleccionado" else "", style = MaterialTheme.typography.titleMedium)
                            Text("${profile.phrase} · ${profile.voice} · ${profile.language}")
                            TextButton(onClick = { stopAll(); runCatching { save(profiles, profile.id); draft = profile }.onFailure { report("No se pudo seleccionar el asistente.") } }) { Text("Seleccionar") }
                            TextButton(onClick = { stopAll(); draft = profile; page = "Personalidad" }) { Text("Editar") }
                            TextButton(onClick = { stopAll(); draft = profile.copy(id = UUID.randomUUID().toString(), name = profile.name.take(33) + " copia"); page = "Personalidad" }) { Text("Duplicar") }
                            TextButton(onClick = { stopAll(); runCatching { save(profiles.filterNot { it.id == profile.id }, selectedId.takeUnless { it == profile.id }) }.onFailure { report("No se pudo eliminar el perfil.") } }) { Text("Eliminar perfil") }
                        } }
                    }
                    Button(onClick = { exportText = AssistantProfile.export(profiles); export.launch("asistentes-pbtmetagpt.json") }, enabled = profiles.isNotEmpty()) { Text("Exportar perfiles sin credenciales") }
                    OutlinedButton(onClick = { stopAll(); import.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar perfiles") }
                }
                "Personalidad", "Nombre del asistente", "Voz" -> {
                    Text("Los cambios se aplican en una sesión nueva. Guarda antes de probar la voz.")
                    if (page == "Voz") Field("Indicaciones de tono, estilo y expresión", draft.tone) { draft = draft.copy(tone = it) }
                    if (page != "Voz") {
                        Field("Nombre del asistente", draft.name) { draft = draft.copy(name = it) }
                        Field("Avatar (emoji o iniciales, hasta 16 caracteres)", draft.avatar) { draft = draft.copy(avatar = it.take(16)) }
                        Text("Frase: ${draft.phrase}")
                        Field("Cómo te llama (opcional)", draft.userName) { draft = draft.copy(userName = it) }
                        Field("Descripción", draft.description) { draft = draft.copy(description = it) }
                        Field("Personalidad", draft.personality) { draft = draft.copy(personality = it) }
                        Field("Forma de hablar y expresión", draft.tone) { draft = draft.copy(tone = it) }
                        Toggle("Tratamiento formal (usted)", draft.formal) { draft = draft.copy(formal = it) }
                        Toggle("Respuestas detalladas habitualmente", draft.detailed) { draft = draft.copy(detailed = it) }
                        Field("Nivel de humor", draft.humor) { draft = draft.copy(humor = it) }
                        Field("Vocabulario", draft.vocabulary) { draft = draft.copy(vocabulary = it) }
                        Field("Instrucciones personalizadas", draft.instructions) { draft = draft.copy(instructions = it) }
                        Field("Mensaje de bienvenida opcional (se reproduce al conectar)", draft.welcomeMessage) { draft = draft.copy(welcomeMessage = it.take(1000)) }
                        Toggle("Sonido de confirmación al activar", draft.confirmationSound) { draft = draft.copy(confirmationSound = it) }
                        Text("Duración máxima de sesión: ${draft.sessionMinutes} minutos")
                        Slider(draft.sessionMinutes.toFloat(), { draft = draft.copy(sessionMinutes = it.toInt()) }, valueRange = 1f..60f, steps = 58)
                    }
                    Field("Idioma (por ejemplo, es-ES)", draft.language) { draft = draft.copy(language = it) }
                    Text("Voz oficial · ${RealtimeSettings.model}. No puede cambiarse tras la primera respuesta de una sesión.")
                    RealtimeSettings.voices.forEach { name -> FilterChip(selected = draft.voice == name, onClick = { draft = draft.copy(voice = name) }, label = { Text(name) }) }
                    Text("Velocidad compatible con gpt-realtime: ${"%.2f".format(java.util.Locale.ROOT, draft.speed)}×")
                    Slider(value = draft.speed.toFloat(), onValueChange = { draft = draft.copy(speed = it.toDouble()) }, valueRange = 0.25f..1.5f)
                    Button(onClick = {
                        stopAll(); runCatching { val valid = draft.validated(); save(profiles.filterNot { it.id == valid.id } + valid, valid.id); draft = valid }
                            .onSuccess { report("Asistente guardado. Se utilizará en la próxima sesión.") }.onFailure { report(it.message ?: "No se pudo guardar el perfil.") }
                    }) { Text("Guardar asistente y voz") }
                    Button(onClick = { wake.stop(); if (configure()) voice.start(backend, token.trim(), bluetooth, sample = true) }, enabled = configured && !vs.active && !vs.connecting && selected == draft) { Text("Escuchar muestra guardada (consume API)") }
                    if (vs.active || vs.connecting) OutlinedButton(onClick = voice::stop) { Text("Cancelar muestra o sesión") }
                    Text(vs.status)
                }
                "Activación por voz" -> {
                    if (selected != null) {
                        PhraseEditor(draft, { draft = it; wake.stop() }, test = { phrase ->
                            stopAll()
                            if (!microphoneReady) requestMic()
                            else runCatching { draft.validated(); wake.test(draft, phrase) }.onFailure { report("Revisa el nombre y las frases antes de probar.") }
                        }, stop = wake::stop)
                        Button(onClick = { stopAll(); runCatching { val valid = draft.validated(); save(profiles.filterNot { it.id == valid.id } + valid, valid.id); draft = valid }
                            .onSuccess { report("Frases y preferencias guardadas.") }.onFailure { report(it.message ?: "No se pudieron guardar las frases.") } }) { Text("Guardar frases y preferencias") }
                    }
                    Text(selected?.let { "Frase actual: «${it.phrase}»" } ?: "Guarda un asistente para construir su frase.")
                    Text("Motor Android exclusivamente local: ${if (wake.available()) "disponible" else "no disponible"}. Necesita el modelo de idioma instalado. No garantiza reconocer cualquier nombre ni escuchar sin interrupciones.")
                    Text("Di únicamente la frase y espera a que aparezca «Escuchando» antes de hacer la petición. No se conserva el audio anterior a la conexión. Durante la sesión puedes seguir preguntando sin repetirla.")
                    Text("Espera limitada a dos minutos con la aplicación visible. El sistema elige la entrada de captura del reconocedor; no se garantiza el micrófono de las gafas. Prueba primero sin Bluetooth. No hay activación desde el firmware ni escucha con pantalla bloqueada.")
                    Toggle("Autorizo la escucha local visible, sin guardar ni enviar su audio", consent) { consent = it; if (!it) { returnToWake = false; wake.stop() } }
                    Toggle("Escuchar también las frases de otros asistentes del mismo idioma", allProfiles) { allProfiles = it; wake.stop() }
                    Text("La sensibilidad acústica no es configurable con este motor. Puedes elegir coincidencia exacta o tolerancia textual por perfil. Si dos asistentes coinciden, no se activa ninguno. Las sugerencias al motor se limitan a 32 Ki caracteres por el tamaño de las transacciones Android; todas las frases siguen participando en la comparación.")
                    Button(onClick = { if (!microphoneReady) requestMic() else if (configure()) selected?.let { wake.start(if (allProfiles) listOf(it) + profiles.filter { p -> p.id != it.id } else listOf(it)) } }, enabled = consent && configured && !vs.active && !vs.connecting) { Text("Activar espera local") }
                    OutlinedButton(onClick = { returnToWake = false; consent = false; stopAll() }) { Text("Desactivar escucha y conversación inmediatamente") }
                    Text("Tras finalizar la sesión se abre otra ventana de espera local mientras mantengas el consentimiento y la aplicación visible. Al navegar o salir se desactiva. No queda una sesión Realtime abierta esperando la palabra.")
                }
                "Permisos" -> {
                    Text("Micrófono: ${if (microphoneReady) "concedido" else "pendiente"}. Puedes usar el teléfono sin gafas. Bluetooth y cámara solo se solicitan al usar esas funciones.")
                    Button(onClick = requestMic) { Text("Solicitar permiso de micrófono") }
                }
            }
        }
    }
}

@Composable private fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, { if (it.length <= 4000) change(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
@Composable private fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(label, Modifier.weight(1f)); Switch(checked, change) }
}
