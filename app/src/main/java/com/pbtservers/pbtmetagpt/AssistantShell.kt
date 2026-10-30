package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AssistantShell(context: Context, vault: KeyVault, voice: RealtimeClient, wake: LocalWakeListener,
    microphoneReady: Boolean, requestMic: () -> Unit, stopAll: () -> Unit,
    report: (String) -> Unit, bindWakeStart: ((AssistantProfile, String) -> Unit) -> Unit,
    themeMode: String = "sistema", setTheme: (String) -> Unit = {},
    deviceSummary: String = "Consulta en Dispositivos el estado real de las gafas.",
    legacy: @Composable (page: String, configured: Boolean, backend: String, token: String, bluetooth: Boolean, setBluetooth: (Boolean) -> Unit, start: () -> Unit, analyzePhoto: (android.graphics.Bitmap) -> Unit) -> Unit,
) {
    val store = remember { ProfileStore(context) }
    val hostedSettings = remember { HostedSettings(context) }
    var groqAudioEnabled by remember { mutableStateOf(hostedSettings.groqAudioAuthorized) }
    val hosted = remember { (context.applicationContext as PbtApplication).hosted }
    var provider by remember { mutableStateOf(hostedSettings.provider) }
    val preferences = remember { context.getSharedPreferences("configuracion", Context.MODE_PRIVATE) }
    val setup = remember { SetupStore(context) }
    var profiles by remember { mutableStateOf(runCatching { store.load() }.getOrElse { report("No se pudieron leer los perfiles. Importa una copia o crea otro."); emptyList() }) }
    var selectedId by rememberSaveable { mutableStateOf(store.selected()) }
    val selected = profiles.find { it.id == selectedId }
    val steps = listOf("Servicio", "Voz", "Escucha")
    val stepPages = listOf("Servicios de IA", "Voz", "Prueba")
    val profileSaver = remember { Saver<AssistantProfile, String>(save = { it.json().toString().takeUnless(SecretPatterns::contains) }, restore = { AssistantProfile.parse(org.json.JSONObject(it), validate = false) }) }
    var draft by rememberSaveable(stateSaver = profileSaver) { mutableStateOf(if (!preferences.getBoolean("bienvenida", false)) runCatching { setup.draft() }.getOrNull() ?: selected ?: AssistantProfile(name = "Asistente") else selected ?: AssistantProfile()) }
    var page by rememberSaveable { mutableStateOf(if (preferences.getBoolean("bienvenida", false)) "Inicio" else stepPages[setup.step]) }
    var wizard by remember { mutableStateOf(!preferences.getBoolean("bienvenida", false)) }
    var step by remember { mutableIntStateOf(setup.step) }
    var advanced by remember { mutableStateOf(false) }
    var inspirationMenu by remember { mutableStateOf(false) }
    var direct by remember { mutableStateOf(preferences.getBoolean("directo", true)) }
    var keyPresent by remember { mutableStateOf(vault.exists()) }
    var keyInput by remember { mutableStateOf("") }
    var backend by remember { mutableStateOf(preferences.getString("backend", "").orEmpty()) }
    LaunchedEffect(backend) { preferences.edit().putString("backend", backend).apply() }
    LaunchedEffect(draft, step, wizard) {
        if (wizard) try { setup.save(step, draft) }
        catch (problem: IllegalArgumentException) { report(problem.message ?: "Revisa el borrador del asistente.") }
        catch (_: IllegalStateException) { report("No se pudo guardar el progreso. Comprueba el almacenamiento.") }
    }
    var token by remember { mutableStateOf("") }
    var bluetooth by remember { mutableStateOf(preferences.getBoolean("audio-bluetooth", false)) }
    LaunchedEffect(bluetooth) { preferences.edit().putBoolean("audio-bluetooth", bluetooth).apply() }
    var consent by remember { mutableStateOf(false) }
    var allProfiles by remember { mutableStateOf(false) }
    var returnToWake by remember { mutableStateOf(false) }
    var hadSession by remember { mutableStateOf(false) }
    var pendingStart by remember { mutableStateOf(false) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val wakeCueScope = rememberCoroutineScope()
    androidx.activity.compose.BackHandler(enabled = !wizard && page != "Inicio") {
        returnToWake = false; consent = false; wake.stop()
        page = when (page) {
            "Conversación", "Asistentes", "Voz", "Ajustes" -> "Inicio"
            "Personalidad", "Nombre del asistente" -> "Asistentes"
            "Biblioteca de voces" -> "Voz"
            else -> "Ajustes"
        }
    }
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                // Leer los MutableState actuales: una selección puede preceder a
                // ON_STOP sin que haya ocurrido todavía otra recomposición.
                if (wizard) try { setup.save(step, draft) }
                catch (_: IllegalArgumentException) { report("El borrador contiene un patrón de credencial y no se ha guardado. Utiliza el campo OpenAI para las claves.") }
                catch (_: IllegalStateException) { report("No se pudo guardar el último cambio del Setup. Comprueba el almacenamiento.") }
                returnToWake = false; consent = false; hadSession = false; keyInput = ""; token = ""
                wake.diagnostics(false)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer); wake.stop()
            (context.applicationContext as PbtApplication).stopVisibleWake = null
        }
    }
    val paidState by voice.state.collectAsStateWithLifecycle()
    val freeState by hosted.state.collectAsStateWithLifecycle()
    val vs = if (provider == "groq") freeState else paidState
    val wakeStatus by wake.status.collectAsStateWithLifecycle()
    var exportText by remember { mutableStateOf("") }
    fun save(list: List<AssistantProfile>, id: String?) {
        store.save(list, id); profiles = list; selectedId = id
    }
    fun advance() {
        returnToWake = false; consent = false; hadSession = false; stopAll()
        try {
            if (step >= 1) {
                val valid = draft.validated()
                save(profiles.filterNot { it.id == valid.id } + valid, valid.id)
                draft = valid
            }
            if (step == 2) { setup.finish(); wizard = false; page = "Inicio" }
            else { setup.save(step + 1, draft); step++; page = stepPages[step]; advanced = false }
        } catch (problem: IllegalArgumentException) { report(problem.message ?: "Revisa los campos de esta etapa.") }
        catch (_: IllegalStateException) { report("No se pudo guardar. Tu configuración continúa en esta pantalla; vuelve a intentarlo.") }
    }
    fun configure(requireProfile: Boolean = true, target: AssistantProfile? = selected): Boolean {
        if (provider == "groq") {
            if (requireProfile && target == null) { report("Crea y guarda un asistente antes de conversar."); return false }
            if (!hostedSettings.authorized || !KeyVault(context, provider = "groq").exists()) { report("Configura una clave Groq Free y su consentimiento en Servicios de IA."); return false }
            return true
        }
        if (target == null && requireProfile) { report("Crea y guarda un asistente antes de iniciar OpenAI."); return false }
        if (voice.state.value.active || voice.state.value.connecting) { report("Finaliza la sesión antes de cambiar la configuración."); return false }
        voice.configure(target, if (direct) ({ vault.read() }) else null)
        (context.applicationContext as? PbtApplication)?.connection = WakeConnection(backend, token.trim(), direct)
        preferences.edit().putBoolean("directo", direct).putString("backend", backend).apply()
        return true
    }
    val configured = selected != null && if (provider == "groq") hostedSettings.authorized && KeyVault(context, provider = "groq").exists() else if (direct) keyPresent else backend.isNotBlank() && token.isNotBlank()
    fun startSelected(target: AssistantProfile? = selected, dictate: Boolean = false, initialQuestion: String = "") {
        if (!configure(target = target)) return
        if (provider == "groq") target?.let { hosted.start(it, dictate, initialQuestion) }
        else voice.start(backend, token.trim(), bluetooth)
    }
    val start: () -> Unit = {
        returnToWake = false; hadSession = false
        wake.stop()
        android.util.Log.i("PbtStart", "CONVERSATION_BUTTON")
        page = "Conversación"
        pendingStart = true
    }
    val persistentPhase by ContinuousWakeService.state.collectAsStateWithLifecycle()
    LaunchedEffect(persistentPhase) {
        if (persistentPhase == ListeningPhase.CONVERSATION) {
            profiles = store.load(); selectedId = store.selected()
        }
    }
    LaunchedEffect(pendingStart, page) {
        if (pendingStart && page == "Conversación") {
            // Primero mostrar el destino; después preparar red y bibliotecas nativas.
            withFrameNanos { }
            pendingStart = false
            android.util.Log.i("PbtStart", "CONVERSATION_SCREEN")
            if (!microphoneReady) requestMic()
            else try {
                selected?.validated()
                startSelected(dictate = provider == "groq")
            } catch (problem: IllegalArgumentException) { report(problem.message ?: "Revisa el asistente y la voz antes de conversar.") }
            catch (_: IllegalStateException) { report("No se pudo preparar la conversación. Finaliza la sesión anterior y vuelve a intentarlo.") }
        } else if (pendingStart) pendingStart = false
    }
    LaunchedEffect(vs.active, vs.connecting) {
        if (returnToWake && vs.active) hadSession = true
        if (returnToWake && hadSession && !vs.active && !vs.connecting) {
            hadSession = false
            if (provider == "groq") hosted.awaitReleased()
            if (consent && owner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) selected?.let { wake.start(if (allProfiles) listOf(it) + profiles.filter { p -> p.id != it.id } else listOf(it)) }
        }
    }
    SideEffect {
        (context.applicationContext as PbtApplication).stopVisibleWake = {
            returnToWake = false; consent = false; hadSession = false; wake.stop()
        }
        bindWakeStart { matched, question ->
        runCatching { save(profiles, matched.id); draft = matched }.onFailure { report("No se pudo seleccionar el asistente activado.") }
        returnToWake = true; page = "Conversación"; wake.stop()
        // El reconocimiento no debe arrancar durante la señal de confirmación ni cortar su inicio.
        wakeCueScope.launch {
            if (matched.confirmationSound) {
                try {
                    WakeConfirmation.play(context,
                        context.getSharedPreferences("configuracion", 0).getBoolean("audio-bluetooth", false))
                } catch (cancel: kotlinx.coroutines.CancellationException) { throw cancel }
                catch (_: Exception) { report("No se pudo reproducir el aviso de activación. Revisa la ruta de audio.") }
            }
            if (!microphoneReady) requestMic() else startSelected(matched, dictate = provider == "groq", initialQuestion = question)
        }
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
        Text("PbtMetaGPT", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (wizard) {
            Text("Configuración inicial · ${step + 1}/${steps.size} · ${steps[step]}", modifier = Modifier.padding(horizontal = 16.dp))
            LinearProgressIndicator(progress = { (step + 1).toFloat() / steps.size }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (step > 0) TextButton(onClick = {
                    returnToWake = false; consent = false; hadSession = false; stopAll()
                    try { setup.save(step - 1, draft); step--; page = stepPages[step]; advanced = false }
                    catch (_: IllegalStateException) { report("No se pudo guardar el progreso. Vuelve a intentarlo.") }
                    catch (problem: IllegalArgumentException) { report(problem.message.orEmpty()) }
                }) { Text("Anterior") }
                Button(onClick = ::advance) { Text(when (step) { 2 -> "Finalizar"; else -> "Continuar" }) }
                if (step == 2) TextButton(onClick = { advance() }) { Text("Omitir") }
            }
        } else if (page !in listOf("Inicio", "Asistentes", "Voz", "Ajustes")) {
            TextButton(onClick = { wake.stop(); page = if (page == "Conversación") "Inicio" else "Ajustes" }) { Text("Volver") }
        }
        if (page != "Inicio" && page != "Conversación" && vs.active) {
            TextButton(onClick = stopAll) { Text("Detener conversación") }
        }
        if (page == "Inicio") Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = androidx.compose.ui.Alignment.TopCenter) {
            HomeDashboard(selected, configured, vs, deviceSummary, microphoneReady, requestMic, start, stopAll, provider = provider, prepareListening = { configure() },
                wakeEnabled = wake.armed, toggleWake = {
                    if (wake.armed) { consent = false; returnToWake = false; wake.stop() }
                    else if (!microphoneReady) requestMic()
                    else if (configure()) { consent = true; selected?.let { wake.start(it) } }
                }) { destination ->
                wake.stop(); page = destination
                if (destination in listOf("Voz", "Activación por voz")) draft = selected ?: draft
            }
        }
        else if (page == "Conversación" && provider == "groq") Box(Modifier.weight(1f)) {
            HostedConversationScreen(context, selected, microphoneReady, requestMic, stopAll, chooseVoice = { stopAll(); draft = selected ?: draft; page = "Voz" }) { stopAll(); page = "Servicios de IA" }
        }
        else if (page == "Registros de errores") Box(Modifier.weight(1f)) { ErrorLogScreen { page = "Diagnóstico" } }
        else if (page in listOf("Inicio", "Conversación", "Dispositivos", "Diagnóstico")) {
            Column(Modifier.weight(1f).fillMaxWidth()) {
            if (page == "Diagnóstico") TextButton(onClick = { page = "Registros de errores" }) { Text("Registros de errores") }
            Box(Modifier.weight(1f)) {
            legacy(page, configured, backend, token, bluetooth, { bluetooth = it }, start) { bitmap ->
                returnToWake = false; consent = false; wake.stop()
                if (provider == "groq") {
                    if (configured) {
                        if (!hosted.state.value.active) selected?.let { hosted.start(it) }
                        hosted.analyzePhoto(bitmap)
                    } else report("Configura tu clave Groq antes de enviar la fotografía.")
                } else if (voice.state.value.active) voice.askImage(bitmap) {}
                else if (configured && configure()) voice.start(backend, token.trim(), bluetooth, image = bitmap)
                else report("Configura OpenAI y guarda un asistente. La foto permanece disponible.")
            }
            }
            }
        } else Column(Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth().align(androidx.compose.ui.Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(page, style = MaterialTheme.typography.headlineMedium)
            when (page) {
                "Conversación y escucha" -> ListeningSettingsScreen(configured, microphoneReady, requestMic, stopAll)
                "ChatGPT oficial" -> OfficialChatGptPanel()
                "Servicios de IA" -> HostedProviderScreen(context, provider, select = {
                    stopAll(); hostedSettings.select(it); provider = it
                }, openOpenAI = { page = "OpenAI" }, compact = wizard)
                "Ajustes" -> {
                    Text("Conexión, privacidad y herramientas del dispositivo", style = MaterialTheme.typography.titleLarge)
                    SettingsMenu { destination ->
                        page = destination
                        if (destination in listOf("Biblioteca de voces", "Personalidad", "Activación por voz")) draft = selected ?: draft
                    }
                    Text("Versión ${BuildConfig.VERSION_NAME} · candidata para validación", style = MaterialTheme.typography.bodySmall)
                    Text("Apariencia", style = MaterialTheme.typography.titleMedium)
                    listOf("sistema", "claro", "oscuro").forEach { mode -> FilterChip(selected = themeMode == mode, onClick = { setTheme(mode) }, label = { Text(mode) }) }
                }
                "Bienvenida" -> {
                    Text("Tu asistente, con tu nombre y tu estilo. Conversa desde el móvil o prueba gafas Meta compatibles con DAT. No necesitas servidor en el modo directo.")
                    Text("Las gafas, el audio y el reconocimiento local necesitan validación en tu dispositivo. Puedes usar siempre el botón manual.")
                    Text("Tres pasos, a tu ritmo. El progreso se guarda automáticamente; puedes volver atrás sin perder opciones. Las gafas son opcionales y todos los ajustes se pueden cambiar después.")
                }
                "Privacidad" -> {
                    Text("BYOK: introduces tu propia clave API. Se cifra con AES-GCM y una clave de Android Keystore; no se incluye en la APK ni en perfiles exportados. Una clave permanente en un móvil tiene riesgo: un proceso comprometido, dispositivo rooteado o depuración puede exponerla mientras se usa. El cifrado no elimina ese riesgo.")
                    Text("Utiliza una clave de proyecto con permisos y presupuesto reducidos. Vigila el consumo y revócala si pierdes el teléfono. El backend opcional evita guardar esa clave en el cliente y añade límites de uso.")
                    Text("ChatGPT Plus y otros planes de ChatGPT no incluyen la facturación de OpenAI API. La voz y las muestras consumen cuota de API.")
                    Text("GroqCloud recibe texto, contexto e instrucciones tras autorización. Qwen3-TTS recibe texto si autorizas su Space. OpenAI solo se utiliza por elección explícita; las fotos exigen confirmación propia. Vosk no transmite audio. La escucha persistente se detiene desde la notificación. Los perfiles se exportan sin cifrar.")
                    Text("No se clonan voces ni se garantiza una interpretación exacta de personajes. Las instrucciones de tono son preferencias, no controles acústicos exactos.")
                }
                "OpenAI", "Comprobar conexión" -> {
                    if (provider != "openai") {
                        Text("OpenAI está desactivado. Configurarlo no autoriza su uso. Selecciónalo expresamente en Servicios de IA para realizar llamadas facturables.")
                        TextButton(onClick = { page = "Servicios de IA" }) { Text("Elegir servicio de conversación") }
                    }
                    Text("OpenAI API tiene facturación independiente. ChatGPT Plus no incluye crédito de API.")
                    Text("Puedes preparar el asistente sin clave y añadirla después desde Más ajustes → OpenAI. No podrás conversar hasta configurar la conexión.")
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
                    Button(onClick = { wake.stop(); if (configure(false)) voice.probeCredentials(backend, token.trim()) }, enabled = provider == "openai" && (if (direct) keyPresent else backend.isNotBlank() && token.isNotBlank()) && !vs.active && !vs.connecting) { Text("Comprobar credenciales sin audio") }
                    Text(vs.status)
                    Text("La comprobación solicita una credencial temporal; no acredita micrófono, altavoz ni gafas. Guarda un asistente antes de conversar.")
                }
                "Asistentes" -> {
                    Text("No hay un personaje obligatorio. Crea el tuyo o elige una inspiración opcional.")
                    Button(onClick = { stopAll(); draft = AssistantProfile.template("Personalizado"); page = "Personalidad" }) { Text("Crear Personalizado") }
                    Box {
                        TextButton(onClick = { inspirationMenu = true }) { Text("Inspiraciones opcionales") }
                        DropdownMenu(expanded = inspirationMenu, onDismissRequest = { inspirationMenu = false }) {
                            listOf("Jarvis", "Friday", "PbtAdmin").forEach { name ->
                                DropdownMenuItem(text = { Text("Crear $name") }, onClick = { stopAll(); draft = AssistantProfile.template(name); page = "Personalidad"; inspirationMenu = false })
                            }
                        }
                    }
                    profiles.forEach { profile ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (profile.id == selectedId) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer)) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(profile.avatar.ifBlank { profile.name.take(2).uppercase(java.util.Locale.forLanguageTag("es-ES")) }, style = MaterialTheme.typography.headlineLarge)
                            Text((if (profile.avatar.isBlank()) profile.name else profile.avatar + " " + profile.name) + if (profile.id == selectedId) " · seleccionado" else "", style = MaterialTheme.typography.titleMedium)
                            if (profile.personality.isNotBlank()) Text(profile.personality.take(240), style = MaterialTheme.typography.bodyLarge)
                            Text("${VoiceSynthesisContract.label(profile.ttsEngine)} · ${profile.ttsVoice.ifBlank { profile.voice }}")
                            TextButton(onClick = { stopAll(); runCatching { save(profiles, profile.id); draft = profile }.onFailure { report("No se pudo seleccionar el asistente.") } }) { Text("Seleccionar") }
                            TextButton(onClick = { stopAll(); draft = profile; page = "Personalidad" }) { Text("Editar asistente") }
                            var actions by remember(profile.id) { mutableStateOf(false) }
                            TextButton(onClick = { actions = !actions }) { Text("Más opciones") }
                            if (actions) {
                            TextButton(onClick = { stopAll(); draft = profile; page = "Voz" }) { Text("Elegir voz") }
                            TextButton(onClick = { stopAll(); draft = profile; page = "Activación por voz" }) { Text("Frases de activación") }
                            TextButton(onClick = { stopAll(); draft = profile.copy(id = UUID.randomUUID().toString(), name = profile.name.take(33) + " copia"); page = "Personalidad" }) { Text("Duplicar") }
                            TextButton(onClick = { stopAll(); runCatching { save(profiles.filterNot { it.id == profile.id }, selectedId.takeUnless { it == profile.id }) }.onFailure { report("No se pudo eliminar el perfil.") } }) { Text("Eliminar perfil") }
                            }
                        } }
                    }
                    Button(onClick = { exportText = AssistantProfile.export(profiles); export.launch("asistentes-pbtmetagpt.json") }, enabled = profiles.isNotEmpty()) { Text("Exportar perfiles sin credenciales") }
                    OutlinedButton(onClick = { stopAll(); import.launch(arrayOf("application/json", "text/plain")) }) { Text("Importar perfiles") }
                }
                "Biblioteca de voces", "Voz" -> {
                if (wizard) Field("Nombre del asistente", draft.name) { draft = draft.copy(name = it) }
                VoiceLibraryScreen(context, draft, { target ->
                    draft = target
                    if (!wizard && runCatching { target.validated() }.isSuccess) {
                        runCatching { save(profiles.filterNot { it.id == target.id } + target, target.id) }
                            .onFailure { report("No se pudo guardar la selección de voz.") }
                    }
                }, save = {
                    runCatching { val valid = draft.validated(); save(profiles.filterNot { it.id == valid.id } + valid, valid.id); draft = valid }
                        .onSuccess { report("Motor y voz guardados. Se utilizarán en una sesión nueva.") }.onFailure { report(it.message ?: "No se pudo guardar el perfil.") }
                }, preview = { target ->
                    runCatching {
                        target.validated()
                        if (provider == "groq" && !HostedPolicy.freeVoice(target.ttsEngine)) report("Elige Android TTS o Qwen3-TTS experimental para muestras gratuitas. No se ha llamado a OpenAI.")
                        else if (target.ttsEngine == "realtime") { if (configure(target = target)) voice.start(backend, token.trim(), bluetooth, sample = true) }
                        else voice.previewAlternative(target, bluetooth)
                    }.onFailure { report("Revisa el perfil y la configuración del motor antes de reproducir la muestra.") }
                }, stop = stopAll, report = report, compact = wizard)
                }
                "Prueba" -> {
                    PhraseEditor(draft, { draft = it; wake.stop() }, test = { phrase ->
                        stopAll()
                        if (!microphoneReady) requestMic() else runCatching { draft.validated(); wake.test(draft, phrase) }
                            .onFailure { report("Revisa el nombre y la frase antes de probar.") }
                    }, stop = wake::stop)
                    Text(wakeStatus)
                    Text("Las gafas son opcionales. Puedes conectarlas después en Ajustes → Dispositivos.")
                    SetupTrial(context, draft, provider, { stopAll(); page = "Servicios de IA" })
                }
                "Personalidad", "Nombre del asistente" -> {
                    Text("Los cambios se aplican en una sesión nueva. Guarda antes de probar la voz.")
                    if (page != "Voz") {
                        Field("Nombre del asistente", draft.name) { draft = draft.copy(name = it) }
                        Field("Personalidad del asistente", draft.personality) { draft = draft.copy(personality = it) }
                        Field("Instrucciones personalizadas", draft.instructions) { draft = draft.copy(instructions = it) }
                        TextButton(onClick = { page = "Voz" }) { Text("Voz: ${VoiceSynthesisContract.label(draft.ttsEngine)}") }
                        val phrase = draft.activationPhrases.firstOrNull { it.primary } ?: draft.activationPhrases.firstOrNull()
                        if (phrase != null) Field("Frase de activación", phrase.rendered(draft.name)) { text ->
                            draft = draft.copy(activationPhrases = draft.activationPhrases.map { if (it.id == phrase.id) it.copy(text = text, appendName = false) else it })
                        }
                        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Ocultar ajustes avanzados" else "Ajustes avanzados del asistente") }
                        if (advanced) {
                        Text("Identidad y relación contigo", style = MaterialTheme.typography.titleMedium)
                        Field("Avatar (emoji o iniciales, hasta 16 caracteres)", draft.avatar) { draft = draft.copy(avatar = it.take(16)) }
                        Text("Frase: ${draft.phrase}")
                        Field("Cómo te llama (opcional)", draft.userName) { draft = draft.copy(userName = it) }
                        Field("Descripción", draft.description) { draft = draft.copy(description = it) }
                        Text("Estilo de conversación", style = MaterialTheme.typography.titleMedium)
                        Field("Forma de hablar y expresión", draft.tone) { draft = draft.copy(tone = it) }
                        Toggle("Tratamiento formal (usted)", draft.formal) { draft = draft.copy(formal = it) }
                        Toggle("Respuestas detalladas habitualmente", draft.detailed) { draft = draft.copy(detailed = it) }
                        Field("Nivel de humor", draft.humor) { draft = draft.copy(humor = it) }
                        Field("Vocabulario", draft.vocabulary) { draft = draft.copy(vocabulary = it) }
                        Text("Audio y límites de la sesión", style = MaterialTheme.typography.titleMedium)
                        Field("Mensaje de bienvenida opcional (se reproduce al conectar)", draft.welcomeMessage) { draft = draft.copy(welcomeMessage = it.take(1000)) }
                        Text("Duración máxima de sesión: ${draft.sessionMinutes} minutos")
                        Slider(draft.sessionMinutes.toFloat(), { draft = draft.copy(sessionMinutes = it.toInt()) }, valueRange = 1f..60f, steps = 58)
                        }
                    }
                    Field("Idioma (por ejemplo, es-ES)", draft.language) { draft = draft.copy(language = it) }
                    Text("Motor y voz actuales: ${VoiceSynthesisContract.label(draft.ttsEngine)}. Configúralos en Voz o Biblioteca de voces; los cambios requieren una sesión nueva.")
                    Button(onClick = {
                        stopAll(); runCatching { val valid = draft.validated(); save(profiles.filterNot { it.id == valid.id } + valid, valid.id); draft = valid }
                            .onSuccess { report("Asistente guardado. Se utilizará en la próxima sesión.") }.onFailure { report(it.message ?: "No se pudo guardar el perfil.") }
                    }) { Text("Guardar asistente y voz") }
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
                    Text(wakeStatus)
                    var advancedListening by rememberSaveable { mutableStateOf(false) }
                    TextButton(onClick = { advancedListening = !advancedListening }) { Text(if (advancedListening) "Ocultar opciones de escucha" else "Opciones avanzadas de escucha") }
                    if (advancedListening) {
                        Toggle("Sonido al reconocer la frase", draft.confirmationSound) { draft = draft.copy(confirmationSound = it) }
                    Text(wakeStatus)
                    var diagnostics by remember { mutableStateOf(false) }
                    val diagnostic by wake.diagnostic.collectAsStateWithLifecycle()
                    Toggle("Diagnóstico de frase (solo en esta pantalla)", diagnostics) { diagnostics = it; wake.diagnostics(it) }
                    DisposableEffect(Unit) { onDispose { wake.diagnostics(false) } }
                    if (diagnostics && diagnostic.isNotBlank()) Text(diagnostic, style = MaterialTheme.typography.bodySmall)
                    ListeningControls(configured, microphoneReady, requestMic, stopVisible = { wake.stop(); voice.stop(); hosted.stop(); configure() })
                    if (provider == "groq") {
                        Toggle("Dictado preciso con Groq Whisper (envía audio)", groqAudioEnabled) {
                            stopAll()
                            try {
                                hostedSettings.authorizeGroqAudio(it)
                                groqAudioEnabled = it
                            } catch (_: Exception) { report("No se pudo guardar la autorización del micrófono remoto."); return@Toggle }
                            report(if (it) "Whisper activado: el audio de cada petición se enviará a Groq tras detectar la frase o pulsar Dictar."
                                else "Whisper desactivado: el dictado vuelve al reconocimiento local.")
                        }
                        Text("La frase de activación sigue reconociéndose localmente. Solo se envía el audio de la petición posterior si autorizas esta opción. Requiere Internet y consume cuota de Groq, con posible coste según tu plan. Para máxima privacidad, mantenlo desactivado.", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(selected?.let { "Frase actual: «${it.phrase}»" } ?: "Guarda un asistente para construir su frase.")
                    Text("Motor Android exclusivamente local: ${if (wake.available()) "disponible" else "no disponible"}. Necesita el modelo de idioma instalado. No garantiza reconocer cualquier nombre ni escuchar sin interrupciones.")
                    Text("Puedes decir la frase y la pregunta juntas. Si haces una pausa, espera la señal antes de preguntar. Solo se conserva en memoria el texto final ya reconocido; no hay grabaciones de fondo.")
                    Text("Prueba Android alternativa: ventanas de hasta dos minutos solo con la aplicación visible. Para segundo plano utiliza el servicio Vosk de arriba. Android elige la entrada del reconocedor; el micrófono de las gafas requiere comprobación física. Ningún motor usa la palabra de activación del firmware Meta.")
                    Toggle("Autorizo la escucha local visible, sin guardar ni enviar su audio", consent) { consent = it; if (!it) { returnToWake = false; wake.stop() } }
                    Toggle("Escuchar también las frases de otros asistentes del mismo idioma", allProfiles) { allProfiles = it; wake.stop() }
                    Text("Baja compara la primera hipótesis; Normal compara tres; Alta compara cinco y admite una diferencia en la última palabra larga. No aumenta la ganancia del micrófono ni identifica al hablante. Las coincidencias entre distintos asistentes se rechazan.")
                    if (selected != draft) Text("Guarda las frases y preferencias antes de activar la espera.")
                    Button(onClick = { if (!microphoneReady) requestMic() else if (configure()) selected?.let { wake.start(if (allProfiles) listOf(it) + profiles.filter { p -> p.id != it.id } else listOf(it)) } }, enabled = consent && configured && selected == draft && !vs.active && !vs.connecting) { Text("Activar espera local") }
                    OutlinedButton(onClick = { returnToWake = false; consent = false; stopAll() }) { Text("Desactivar escucha y conversación inmediatamente") }
                    Text("Tras finalizar la sesión se abre otra ventana de espera local mientras mantengas el consentimiento y la aplicación visible. Al navegar o salir se desactiva. No queda una sesión Realtime abierta esperando la palabra.")
                    }
                }
                "Permisos" -> {
                    Text("Micrófono: ${if (microphoneReady) "concedido" else "pendiente"}. Puedes usar el teléfono sin gafas. Bluetooth y cámara solo se solicitan al usar esas funciones.")
                    Button(onClick = requestMic) { Text("Solicitar permiso de micrófono") }
                }
            }
        }
        if (!wizard) NavigationBar {
            listOf("Inicio" to R.drawable.ic_home, "Asistentes" to R.drawable.ic_assistants,
                "Voz" to R.drawable.ic_voice, "Ajustes" to R.drawable.ic_settings).forEach { (destination, icon) ->
                NavigationBarItem(selected = NavigationSection.forPage(page) == destination, onClick = {
                    returnToWake = false; consent = false
                    wake.stop()
                    if (!ContinuousWakeService.running && destination !in listOf("Inicio", "Conversación", "Dispositivos")) stopAll()
                    page = destination
                    if (destination == "Voz") draft = selected ?: draft
                }, icon = { Icon(androidx.compose.ui.res.painterResource(icon), contentDescription = null) },
                    label = { Text(if (destination == "Voz") "Voces" else destination, maxLines = 1, softWrap = false) })
            }
        }
    }
}

@Composable private fun Field(label: String, value: String, change: (String) -> Unit) = OutlinedTextField(value, { if (it.length <= 4000) change(it) }, label = { Text(label) }, modifier = Modifier.fillMaxWidth())
@Composable internal fun Toggle(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) { Text(label, Modifier.weight(1f)); Switch(checked, change) }
}
