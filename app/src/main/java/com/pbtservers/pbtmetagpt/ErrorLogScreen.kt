package com.pbtservers.pbtmetagpt

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable fun ErrorLogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as PbtApplication
    val scope = rememberCoroutineScope()
    var content by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    suspend fun reload() {
        try { content = withContext(Dispatchers.IO) { app.errorReport() } }
        catch (_: IOException) { status = "No se pudieron leer los registros. Comprueba el almacenamiento." }
        catch (_: SecurityException) { status = "Android ha denegado el acceso al historial privado." }
    }
    LaunchedEffect(Unit) { reload() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: throw IOException()
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(app.errorReport()) }
                }
                status = "Informe exportado. Revísalo antes de compartirlo."
            } catch (_: IOException) { status = "No se pudo escribir el archivo. Elige otra ubicación." }
            catch (_: SecurityException) { status = "El proveedor de documentos ha denegado el acceso." }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Registros de errores", style = MaterialTheme.typography.headlineMedium)
        Text("Se conservan fallos técnicos durante siete días. No se guardan mensajes de excepción ni contenido privado. Algunos abortos nativos, cortes de batería o cierres del sistema no pueden registrar su pila desde la aplicación.")
        Text("Después de un cierre: vuelve a abrir PbtMetaGPT, entra aquí y exporta el informe. Indica aparte la acción que estabas realizando.")
        Button(onClick = { export.launch("pbtmetagpt-errores.txt") }) { Text("Exportar registros .txt") }
        OutlinedButton(onClick = { scope.launch { reload() } }) { Text("Actualizar registros") }
        OutlinedButton(onClick = { confirmClear = true }) { Text("Borrar historial de errores") }
        TextButton(onClick = onBack) { Text("Volver a Diagnóstico") }
        if (status.isNotBlank()) Text(status)
        Text(content, style = MaterialTheme.typography.bodySmall)
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("¿Borrar registros?") },
        text = { Text("También se eliminará el informe del último cierre registrado.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; scope.launch { withContext(Dispatchers.IO) { app.journal.clear() }; reload() } }) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } })
}
