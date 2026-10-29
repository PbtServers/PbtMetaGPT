package com.pbtservers.pbtmetagpt

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable fun ListeningSettingsScreen(configured: Boolean, microphoneReady: Boolean, requestMic: () -> Unit, stop: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { ListeningSettings(context) }
    var value by remember { mutableStateOf(settings.load()) }
    var error by remember { mutableStateOf("") }
    fun save(next: ListeningOptions) {
        try { settings.save(next); value = next; error = "" }
        catch (_: Exception) { error = "Revisa los valores; no se ha guardado el cambio." }
    }
    Toggle("Conversación continua", value.continuous) { stop(); save(value.copy(continuous = it)) }
    Text("Sin caducidad, cada respuesta vuelve a la escucha hasta que digas Termina. El modo continuo usa Vosk español; instala su modelo en Activación por voz.")
    Toggle("Escuchar frase de activación en segundo plano", value.background) {
        if (!it) ContinuousWakeService.stop(context)
        save(value.copy(background = it))
    }
    Text("Recordar esta opción no inicia el micrófono. Usa Activar escucha con la aplicación visible. No se reinicia tras forzar detención ni al encender el teléfono.")
    ListeningControls(configured, microphoneReady, requestMic, stop)
    Toggle("Permitir interrupción por voz", value.interruption) { stop(); save(value.copy(interruption = it)) }
    Toggle("Reconocer «Para»", value.recognizeStop) { stop(); save(value.copy(recognizeStop = it)) }
    Text("Sensibilidad de interrupción: ${(value.sensitivity * 100).toInt()} %")
    Slider(value.sensitivity, onValueChange = { value = value.copy(sensitivity = it) }, onValueChangeFinished = { save(value) })
    Text("A mayor sensibilidad, menor confianza exigida al reconocedor. Durante el audio se exige AEC; si la ruta no lo permite, usa Parar. Validación acústica pendiente.")
    Toggle("Finalizar por inactividad", value.inactivitySeconds > 0) { save(value.copy(inactivitySeconds = if (it) 120 else 0)) }
    if (value.inactivitySeconds > 0) {
        Text("Inactividad: ${value.inactivitySeconds} segundos")
        Slider(value.inactivitySeconds.toFloat(), { value = value.copy(inactivitySeconds = it.toInt()) },
            valueRange = 30f..3600f, onValueChangeFinished = { save(value) })
    }
    var phrase by remember { mutableStateOf(value.endPhrase) }
    OutlinedTextField(phrase, { phrase = it.take(80) }, label = { Text("Frase de finalización") }, modifier = Modifier.fillMaxWidth())
    TextButton(onClick = { save(value.copy(endPhrase = phrase)) }) { Text("Guardar frase de finalización") }
    Toggle("«Hasta luego» finaliza la conversación", value.goodbye) { save(value.copy(goodbye = it)) }
    Text("Para: interrumpe y permite continuar. Termina: cierra y recupera la espera autorizada. Deja de escuchar: detiene también el servicio.")
    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
}

@Composable fun OfficialChatGptPanel() {
    val context = LocalContext.current
    val app = context.applicationContext as PbtApplication
    var installed by remember { mutableStateOf(OfficialChatGpt(context).installed()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) installed = OfficialChatGpt(context).installed()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Text("ChatGPT oficial", style = MaterialTheme.typography.titleLarge)
    Text(if (installed) "Aplicación de OpenAI instalada" else "Aplicación de OpenAI no instalada")
    Button(onClick = app::openChatGpt) { Text("Abrir ChatGPT") }
    Text("Puedes decir «Abre GPT Live», «Abre ChatGPT», «Inicia ChatGPT en voz» o «Quiero hablar con ChatGPT». Se cancela la respuesta y se liberan captura, ruta y foco antes de abrir la aplicación oficial.")
    Text(ChatGptLaunchPolicy.voiceInstructions)
    Text("No se ha encontrado un intent, deep link ni acción pública documentada que garantice iniciar Live desde otra app. Los accesos directos de un launcher no conceden a PbtMetaGPT acceso a componentes privados. Con la pantalla bloqueada o en segundo plano se muestra una notificación de apertura.")
    Text("Al regresar, pulsa Hablar o Activar escucha. La captura permanece detenida para evitar interferencias.")
}
