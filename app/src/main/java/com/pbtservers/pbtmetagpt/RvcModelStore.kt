package com.pbtservers.pbtmetagpt

import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/** Inventario de modelos RVC no confiables. Estos archivos nunca se deserializan ni ejecutan. */
data class RvcModel(val id: String, val name: String, val modelBytes: Long, val indexBytes: Long) {
    val readyForInference = false
}

class RvcModelStore(private val root: File) {
    companion object {
        private const val MAX_MODEL = 300L * 1024 * 1024
        private const val MAX_INDEX = 128L * 1024 * 1024
        private val safeName = Regex("[\\p{L}\\p{N} _().+\\-]{1,150}")
    }
    private fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        return File(root, id).canonicalFile.also { require(it.parentFile == root.canonicalFile) }
    }
    fun list(): List<RvcModel> = root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { directory ->
            runCatching {
                val model = File(directory, "model.pth")
                if (!model.isFile || model.length() !in 1..MAX_MODEL) return@runCatching null
                val index = File(directory, "model.index")
                RvcModel(directory.name, File(directory, "name.txt").readText().take(150),
                    model.length(), if (index.isFile) index.length() else 0)
            }.getOrNull()
        }.sortedBy { it.name.lowercase() }

    fun install(modelName: String, model: InputStream, index: InputStream? = null, checkActive: () -> Unit = {}): RvcModel {
        require(modelName.endsWith(".pth", true) && safeName.matches(modelName))
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val temp = File(root, ".$id").canonicalFile
        require(temp.parentFile == root.canonicalFile && temp.mkdir())
        try {
            copyBounded(model, File(temp, "model.pth"), MAX_MODEL, checkActive)
            if (index != null) copyBounded(index, File(temp, "model.index"), MAX_INDEX, checkActive)
            File(temp, "name.txt").writeText(modelName.removeSuffix(".pth").take(150), Charsets.UTF_8)
            checkActive()
            require(temp.renameTo(directory(id)))
            return requireNotNull(list().firstOrNull { it.id == id })
        } finally { if (temp.exists()) temp.deleteRecursively() }
    }

    fun installZip(input: InputStream, checkActive: () -> Unit = {}): RvcModel {
        root.mkdirs()
        val id = UUID.randomUUID().toString()
        val temp = File(root, ".$id").canonicalFile
        require(temp.parentFile == root.canonicalFile && temp.mkdir())
        try {
            var selectedName: String? = null
            var foundModel = false
            var foundIndex = false
            var entries = 0
            ZipInputStream(input).use { zip ->
                while (true) {
                    checkActive()
                    val entry = zip.nextEntry ?: break
                    require(++entries <= 128) { "ZIP demasiado complejo." }
                    val path = entry.name.replace('\\', '/')
                    require(!path.startsWith("/") && path.split('/').none { it == ".." || it == "." })
                    val name = path.substringAfterLast('/')
                    require(name.length <= 150)
                    if (!entry.isDirectory) {
                        when {
                            name.endsWith(".pth", true) -> {
                                require(!foundModel && safeName.matches(name)) { "El ZIP debe contener un único modelo RVC." }
                                copyBounded(zip, File(temp, "model.pth"), MAX_MODEL, checkActive)
                                selectedName = name; foundModel = true
                            }
                            name.endsWith(".index", true) -> {
                                require(!foundIndex && safeName.matches(name)) { "El ZIP contiene más de un índice." }
                                copyBounded(zip, File(temp, "model.index"), MAX_INDEX, checkActive)
                                foundIndex = true
                            }
                            name.endsWith(".txt", true) || name.endsWith(".md", true) ||
                                name.endsWith(".json", true) || name.endsWith(".png", true) ||
                                name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) ||
                                name.endsWith(".wav", true) || name == ".DS_Store" -> {
                                // Descartar metadatos sin extraerlos y sin descomprimir ZIPs enormes.
                                val skip = ByteArray(8192)
                                var skipped = 0L
                                while (true) {
                                    checkActive()
                                    val read = zip.read(skip)
                                    if (read < 0) break
                                    skipped += read
                                    require(skipped <= 2L * 1024 * 1024) { "Metadatos ZIP demasiado grandes." }
                                }
                            }
                            else -> error("El ZIP contiene extensiones no permitidas.")
                        }
                    }
                    zip.closeEntry()
                }
            }
            require(foundModel) { "El ZIP no contiene un archivo .pth válido." }
            File(temp, "name.txt").writeText(requireNotNull(selectedName).substringBeforeLast(".").take(150), Charsets.UTF_8)
            checkActive()
            require(temp.renameTo(directory(id)))
            return requireNotNull(list().firstOrNull { it.id == id })
        } finally { if (temp.exists()) temp.deleteRecursively() }
    }

    fun attachIndex(id: String, input: InputStream, checkActive: () -> Unit = {}) {
        val directory = directory(id)
        require(File(directory, "model.pth").isFile)
        val temporary = File(directory, "index.pending")
        try {
            copyBounded(input, temporary, MAX_INDEX, checkActive)
            checkActive()
            val index = File(directory, "model.index")
            require(!index.exists()) { "El modelo ya tiene un índice; impórtalo de nuevo para sustituirlo." }
            require(temporary.renameTo(index))
        } finally { if (temporary.exists()) temporary.delete() }
    }

    fun delete(id: String) {
        val directory = directory(id)
        require(directory.isDirectory && directory.deleteRecursively())
    }

    private fun copyBounded(input: InputStream, destination: File, limit: Long, checkActive: () -> Unit) {
        destination.outputStream().use { output ->
            val buffer = ByteArray(65536)
            var size = 0L
            while (true) {
                checkActive()
                val n = input.read(buffer)
                if (n < 0) break
                size += n
                require(size <= limit) { "Archivo RVC demasiado grande." }
                output.write(buffer, 0, n)
            }
            require(size > 0L) { "Archivo RVC vacío." }
        }
    }
}
