package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ConversationInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var voice: RealtimeClient? = null
    private var wake: LocalWakeListener? = null

    @After fun cleanup() {
        instrumentation.runOnMainSync { voice?.stop(); wake?.stop(); scope.cancel() }
        context.getSharedPreferences("configuracion", Context.MODE_PRIVATE).edit().clear().commit()
        KeyVault(context).delete()
        context.getSharedPreferences("servicios_alojados", 0).edit().clear().commit()
        ProfileStore(context).save(emptyList(), null)
    }

    @Test fun hablarAbreConversacionYUnFalloDeCredencialNoCierraLaPantalla() {
        val profile = AssistantProfile(name = "Luna")
        ProfileStore(context).save(listOf(profile), profile.id)
        context.getSharedPreferences("configuracion", 0).edit().clear().putBoolean("bienvenida", true).commit()
        // Presencia de credencial con almacenamiento dañado: falla antes de red/audio,
        // sin claves reales ni permisos de sensores concedidos al proceso.
        context.getSharedPreferences("credenciales_cifradas", 0).edit().clear().putString("ciphertext", "dato-invalido").commit()
        HostedSettings(context).select("openai")
        var failure by mutableStateOf<String?>(null)
        val client = RealtimeClient(context, scope, { failure = it }).also { voice = it }
        val listener = LocalWakeListener(context, scope) {}.also { wake = it }
        compose.setContent {
            PbtTheme { AssistantShell(context, KeyVault(context), client, listener,
                microphoneReady = true, requestMic = { fail("No debe pedir permisos en este caso controlado") },
                stopAll = client::stop, report = { failure = it }, bindWakeStart = {}) { page, _, _, _, _, _, start, _ ->
                Column {
                    Text("Pantalla $page")
                    Button(onClick = start) { Text("Hablar con ChatGPT") }
                    failure?.let { Text("Error recuperable") }
                }
            } }
        }
        // Sin credencial válida, el botón de inicio está deshabilitado.
        // La navegación permite comprobar la pantalla y el fallo recuperable
        // sin fabricar una clave ni conceder permisos.
        compose.onNodeWithText("Hablar").performClick()
        compose.onNodeWithText("Pantalla Conversación").assertIsDisplayed()
        compose.onNodeWithText("Hablar con ChatGPT").performClick()
        compose.waitUntil(5_000) { failure != null }
        compose.onNodeWithText("Pantalla Conversación").assertIsDisplayed()
        assertFalse(client.state.value.active)
        assertFalse(client.state.value.connecting)
        compose.onNodeWithText("Hablar con ChatGPT").performClick()
        compose.onNodeWithText("Pantalla Conversación").assertIsDisplayed()
    }
}
