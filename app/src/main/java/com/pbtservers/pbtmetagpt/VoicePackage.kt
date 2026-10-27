package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipInputStream

data class VoicePackage(val id: String, val name: String, val language: String, val license: String, val source: String,
    val sampleText: String, val minRamMb: Int, val bytes: Long, val metadata: String) {
    val format get() = "sherpa-piper-phonemes-v1"
    val engine get() = "Sherpa ONNX 2.0.1 · CPU · 64 bits"
}

/** Solo datos: cuatro nombres fijos, límites reales de descompresión, hashes y publicación atómica. */
class VoicePackageStore(private val root: File) {
    companion object {
        private val files = mapOf("voz.json" to 65536L, "model.onnx" to 268435456L, "tokens.txt" to 2097152L, "lexicon.tsv" to 16777216L)
        private val fields = setOf("version", "name", "format", "engine", "engineVersion", "language", "license", "source", "sampleText", "minRamMb", "rightsConfirmed", "sha256")
        fun digest(file: File): String = file.inputStream().use { input ->
            val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
            while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
            hash.digest().joinToString("") { "%02x".format(it) }
        }
    }
    private fun directory(id: String): File {
        require(id.matches(Regex("[a-f0-9-]{36}"))) { "Identificador de paquete no válido." }
        val path = File(root, id).canonicalFile
        require(path.parentFile == root.canonicalFile) { "Ruta de paquete no permitida." }
        return path
    }
    fun file(id: String, name: String): File { require(name in files); return File(directory(id), name) }
    fun list(): List<VoicePackage> = root.listFiles().orEmpty().filter { it.isDirectory && !it.name.startsWith(".") }
        .mapNotNull { runCatching { describe(it.name) }.getOrNull() }.sortedBy { it.name }
    fun describe(id: String): VoicePackage {
        val dir = directory(id); val text = File(dir, "voz.json").readText(Charsets.UTF_8); val j = validateMetadata(text)
        return VoicePackage(id, j.getString("name"), j.getString("language"), j.getString("license"), j.getString("source"),
            j.getString("sampleText"), j.getInt("minRamMb"), files.keys.sumOf { File(dir, it).length() }, text)
    }
    fun install(input: InputStream, checkActive: () -> Unit = {}): VoicePackage {
        root.mkdirs(); require(root.usableSpace > 320L * 1024 * 1024) { "Se necesitan al menos 320 MiB libres para instalar un paquete de forma segura." }
        val id = UUID.randomUUID().toString(); val staging = File(root, ".$id").canonicalFile
        require(staging.parentFile == root.canonicalFile); check(staging.mkdir())
        try {
            val seen = mutableSetOf<String>()
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val limit = files[entry.name] ?: error("El paquete contiene archivos o rutas no permitidos.")
                    require(!entry.isDirectory && seen.add(entry.name)) { "El paquete contiene entradas duplicadas o directorios." }
                    var size = 0L; val buffer = ByteArray(65536)
                    File(staging, entry.name).outputStream().use { output ->
                        while (true) { checkActive(); val n = zip.read(buffer); if (n < 0) break; size += n; require(size <= limit) { "Un archivo supera su límite técnico de tamaño." }; output.write(buffer, 0, n) }
                    }
                    require(size > 0) { "El paquete contiene un archivo vacío." }; zip.closeEntry()
                }
            }
            require(seen == files.keys) { "Faltan metadatos, modelo, símbolos o diccionario de pronunciaciones." }
            val metadata = validateMetadata(File(staging, "voz.json").readText(Charsets.UTF_8))
            val hashes = metadata.getJSONObject("sha256")
            require(hashes.keys().asSequence().toSet() == files.keys - "voz.json") { "El manifiesto debe identificar todos los datos por SHA-256." }
            hashes.keys().forEach { name -> require(hashes.getString(name).matches(Regex("[a-f0-9]{64}")) && digest(File(staging, name)) == hashes.getString(name)) { "El contenido no coincide con sus hashes." } }
            OnnxMetadata.validate(File(staging, "model.onnx"))
            PronunciationLexicon.load(File(staging, "lexicon.tsv"), File(staging, "tokens.txt")).phonemes(metadata.getString("sampleText"))
            check(staging.renameTo(directory(id))) { "No se pudo publicar el paquete instalado." }
            return describe(id)
        } finally { if (staging.exists()) staging.deleteRecursively() }
    }
    fun delete(id: String) { val dir = directory(id); check(!dir.exists() || dir.deleteRecursively()) { "No se pudo liberar el almacenamiento del modelo." } }
    private fun validateMetadata(text: String): JSONObject {
        require(text.toByteArray(Charsets.UTF_8).size <= 65536 && !SecretPatterns.contains(text)) { "Metadatos demasiado grandes o con patrones de secretos." }
        val j = JSONObject(text)
        require(j.keys().asSequence().toSet() == fields && j.getInt("version") == 1) { "Esquema de paquete no compatible; no se admiten credenciales ni campos extra." }
        require(j.getString("format") == "sherpa-piper-phonemes-v1" && j.getString("engine") == "sherpa-onnx" && j.getString("engineVersion") == "2.0.1") { "Formato o versión de motor no compatible." }
        require(listOf("name", "license", "source", "sampleText").all { j.getString(it).length in 1..1000 }) { "Faltan nombre, licencia, procedencia o texto de prueba." }
        require(j.getString("language").matches(Regex("[a-z]{2,3}(-[A-Za-z]{2,8})?")) && j.getInt("minRamMb") in 512..8192 && j.getBoolean("rightsConfirmed")) { "Idioma, requisitos o declaración de autorización no válidos." }
        return j
    }
}

/** Pronunciaciones explícitas; no inventa G2P ni presupone que un modelo incluya fonemizador. */
class PronunciationLexicon private constructor(private val words: Map<String, String>, private val symbols: Set<Int>) {
    fun phonemes(text: String): IntArray {
        require(text.length in 1..4000 && !SecretPatterns.contains(text)) { "Texto de síntesis demasiado largo o con patrones de secretos." }
        val tokens = Regex("[\\p{L}\\p{M}\\p{N}]+|[.,!?;:]").findAll(text.lowercase(java.util.Locale.ROOT)).map { it.value }.toList()
        require(tokens.isNotEmpty()) { "No hay texto pronunciable." }
        val result = tokens.map { token ->
            if (token.length == 1 && token[0] in ".,!?;:") token.takeIf { it[0].code in symbols }.orEmpty()
            else words[token] ?: throw IllegalArgumentException("El diccionario del modelo no cubre esta respuesta. Amplía sus pronunciaciones o elige otra voz.")
        }.joinToString(" ")
        val points = result.codePoints().toArray()
        require(points.size <= 32768) { "Las pronunciaciones superan el límite seguro de comunicación con el proceso aislado." }
        require(points.all { it in symbols }) { "Las pronunciaciones usan símbolos que el modelo no admite." }
        return points
    }
    companion object {
        fun load(lexicon: File, tokens: File): PronunciationLexicon {
            val symbols = tokens.readLines(Charsets.UTF_8).mapNotNull { line ->
                val symbol = line.substringBeforeLast(' ', "")
                symbol.codePoints().toArray().singleOrNull()
            }.toSet()
            require(' '.code in symbols && symbols.size >= 10) { "Tabla de símbolos no compatible." }
            val words = mutableMapOf<String, String>()
            lexicon.forEachLine(Charsets.UTF_8) { line ->
                val parts = line.split('\t')
                require(parts.size == 2 && parts[0].matches(Regex("[\\p{L}\\p{M}\\p{N}]+")) && parts[1].isNotBlank() && parts[1].length <= 200) { "Diccionario TSV dañado o no compatible." }
                require(words.put(parts[0].lowercase(java.util.Locale.ROOT), parts[1]) == null) { "Diccionario con palabras duplicadas." }
                require(parts[1].codePoints().allMatch { it in symbols }) { "El diccionario contiene símbolos no compatibles." }
            }
            require(words.isNotEmpty()); return PronunciationLexicon(words, symbols)
        }
    }
}

/** Lector protobuf acotado: rechaza archivos truncados y verifica metadatos VITS/Piper antes de usar JNI. */
object OnnxMetadata {
    fun validate(file: File): Map<String, String> = RandomAccessFile(file, "r").use { input ->
        require(input.length() in 128..268435456) { "Tamaño de modelo ONNX no compatible." }
        var ir = false; var graph = false; val metadata = mutableMapOf<String, String>()
        fun varint(end: Long): Long {
            var value = 0L
            for (i in 0..9) { require(input.filePointer < end); val b = input.readUnsignedByte(); value = value or ((b and 127).toLong() shl (7 * i)); if (b < 128) { require(value >= 0); return value } }
            error("Protobuf no válido")
        }
        fun text(end: Long): String { val n = varint(end); require(n <= 4096 && input.filePointer + n <= end); return ByteArray(n.toInt()).also { input.readFully(it) }.toString(Charsets.UTF_8) }
        while (input.filePointer < input.length()) {
            val tag = varint(input.length()); val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                0 -> { val value = varint(input.length()); if (field == 1) ir = value in 3..20 }
                1 -> { require(input.filePointer + 8 <= input.length()); input.seek(input.filePointer + 8) }
                5 -> { require(input.filePointer + 4 <= input.length()); input.seek(input.filePointer + 4) }
                2 -> {
                    val length = varint(input.length()); val end = input.filePointer + length
                    require(length >= 0 && end in input.filePointer..input.length())
                    if (field == 7) graph = length > 0
                    if (field == 14) {
                        var key = ""; var value = ""
                        while (input.filePointer < end) { val t = varint(end); require(t == 10L || t == 18L); if (t == 10L) key = text(end) else value = text(end) }
                        if (key in setOf("model_type", "sample_rate", "num_speakers", "is_piper")) metadata[key] = value
                    }
                    input.seek(end)
                }
                else -> error("Modelo protobuf no compatible")
            }
        }
        require(ir && graph && metadata["model_type"] == "vits" && (metadata["sample_rate"]?.toIntOrNull() ?: 0) in 8000..48000 && metadata["is_piper"] == "1") { "El ONNX no acredita los metadatos VITS/Piper compatibles. No se cargará como una voz." }
        metadata
    }
}
