package com.pbtservers.pbtmetagpt

import android.content.Context
import java.io.File
import java.io.InputStream
import java.util.UUID

/** Clasificación explícita de los tres componentes; no interpreta archivos PyTorch. */
object RvcOnnxNames {
    fun kind(filename: String): String {
        require(filename.length in 6..180 && filename.endsWith(".onnx", true))
        return when {
            filename.contains("rmvpe", true) -> "rmvpe"
            filename.contains("hubert", true) || filename.contains("content", true) -> "hubert"
            filename.contains("synth", true) || filename.contains("rvc", true) -> "synth"
            else -> throw IllegalArgumentException("No se reconoce el componente RVC ONNX.")
        }
    }
}

/**
 * Modelos RVC ONNX elegidos por el usuario. No se interpreta ni se ejecuta
 * ningún checkpoint PyTorch .pth y ningún peso se envía a Internet.
 */
class RvcOnnxStore(private val context: Context) {
    private val parent = File(context.filesDir, "rvc_onnx")
    private val backup = File(context.filesDir, "rvc_onnx_backup")
    private val prefs = context.getSharedPreferences("rvc_onnx", Context.MODE_PRIVATE)
    val enabled: Boolean get() = prefs.getBoolean("enabled", false) && ready()
    fun setEnabled(value: Boolean) {
        if (value) require(ready()) { "Importa los tres ONNX primero." }
        check(prefs.edit().putBoolean("enabled", value).commit())
    }
    fun ready(): Boolean = listOf("synth.onnx", "hubert.onnx", "rmvpe.onnx")
        .all { File(parent, it).let { file -> file.isFile && file.length() in 1000L..400_000_000L } }
    fun files(): Triple<File, File, File> {
        require(ready()) { "Falta el sintetizador, HuBERT o RMVPE RVC ONNX." }
        return Triple(File(parent, "synth.onnx"), File(parent, "hubert.onnx"), File(parent, "rmvpe.onnx"))
    }
    fun install(models: Map<String, InputStream>, checkActive: () -> Unit = {}) {
        require(models.keys == setOf("synth", "hubert", "rmvpe")) { "Selecciona exactamente tres modelos ONNX RVC." }
        // Restaurar una versión anterior si Android finalizó una instalación
        // entre el renombrado inicial y la confirmación de la nueva carpeta.
        if (!parent.exists() && backup.isDirectory) require(backup.renameTo(parent))
        val temporary = File(context.filesDir, "rvc_onnx_" + UUID.randomUUID())
        require(temporary.mkdir())
        try {
            for ((name, stream) in models) {
                stream.use { source ->
                    File(temporary, name + ".onnx").outputStream().use { destination ->
                        var size = 0L
                        val bytes = ByteArray(64 * 1024)
                        while (true) {
                            checkActive()
                            val n = source.read(bytes)
                            if (n == -1) break
                            size += n
                            require(size <= 400_000_000L) { "Archivo RVC ONNX demasiado grande." }
                            destination.write(bytes, 0, n)
                        }
                        require(size >= 1000L) { "Archivo RVC ONNX vacío o incompleto." }
                    }
                }
            }
            checkActive()
            // No activar un modelo nuevo sin completar los tres archivos.
            setEnabled(false)
            if (backup.exists()) require(backup.deleteRecursively())
            if (parent.exists()) require(parent.renameTo(backup))
            if (!temporary.renameTo(parent)) {
                if (backup.exists()) require(backup.renameTo(parent))
                error("No se pudo instalar el conjunto RVC local.")
            }
            if (backup.exists()) require(backup.deleteRecursively())
        } finally { if (temporary.exists()) temporary.deleteRecursively() }
    }
    fun clear() {
        setEnabled(false)
        if (parent.exists()) check(parent.deleteRecursively())
    }
}
