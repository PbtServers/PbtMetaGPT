package com.pbtservers.pbtmetagpt

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class VoicePackageTest {
    private fun varint(value: Int): ByteArray { var n = value; val out = ByteArrayOutputStream(); while (n >= 128) { out.write((n and 127) or 128); n = n ushr 7 }; out.write(n); return out.toByteArray() }
    private fun field(tag: Int, text: ByteArray) = varint(tag) + varint(text.size) + text
    /** Protobuf estructural ficticio, deliberadamente sin pesos. Nunca se usa para acreditar inferencia. */
    private fun structuralOnnx(): ByteArray = byteArrayOf(8, 8) + field(18, "prueba estructural sin inferencia".repeat(3).toByteArray()) + field(58, byteArrayOf(10, 0)) +
        listOf("model_type" to "vits", "is_piper" to "1", "sample_rate" to "22050").fold(byteArrayOf()) { all, (k, v) -> all + field(114, field(10, k.toByteArray()) + field(18, v.toByteArray())) }
    private fun files(): MutableMap<String, ByteArray> {
        val files = mutableMapOf("model.onnx" to structuralOnnx(), "tokens.txt" to ("  0\n" + "abcdefghij".mapIndexed { i, c -> "$c ${i + 1}\n" }.joinToString("")).toByteArray(), "lexicon.tsv" to "hola\thab\n".toByteArray())
        val hashes = JSONObject(); files.forEach { (name, bytes) -> hashes.put(name, MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }) }
        files["voz.json"] = JSONObject().put("version", 1).put("name", "Prueba estructural").put("format", "sherpa-piper-phonemes-v1").put("engine", "sherpa-onnx").put("engineVersion", "2.0.1").put("language", "es-ES").put("license", "Datos ficticios de prueba").put("source", "Suite automática, no una voz real").put("sampleText", "hola").put("minRamMb", 512).put("rightsConfirmed", true).put("sha256", hashes).toString().toByteArray()
        return files
    }
    private fun zip(files: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { zip -> files.forEach { (name, bytes) -> zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry() } } }.toByteArray()
    @Test fun `instala solo datos estructurales enumera y libera almacenamiento`() {
        val root = Files.createTempDirectory("biblioteca-voces-").toFile(); val store = VoicePackageStore(root)
        try {
            val pack = store.install(zip(files()).inputStream())
            assertEquals(pack, store.list().single()); assertTrue(pack.bytes > 0)
            store.delete(pack.id); assertTrue(store.list().isEmpty()); assertFalse(File(root, pack.id).exists())
        } finally { root.deleteRecursively() }
    }
    @Test fun `rechaza rutas codigo pesos alterados y formatos ajenos sin residuos`() {
        val root = Files.createTempDirectory("biblioteca-voces-").toFile(); val store = VoicePackageStore(root)
        try {
            val attempts = listOf(files().apply { put("../fuera.txt", byteArrayOf(1)) }, files().apply { put("motor.so", byteArrayOf(1)) }, files().apply { put("model.onnx", byteArrayOf(1, 2, 3)) }, files().apply { remove("tokens.txt") }, files().apply {
                put("voz.json", JSONObject(getValue("voz.json").toString(Charsets.UTF_8)).put("engine", "arbitrario").toString().toByteArray())
            })
            attempts.forEach { assertTrue(runCatching { store.install(zip(it).inputStream()) }.isFailure); assertTrue(root.listFiles().orEmpty().isEmpty()) }
            assertTrue(runCatching { store.delete("../../otro") }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun `rechaza secretos en metadatos y autorizacion ausente`() {
        val root = Files.createTempDirectory("biblioteca-voces-").toFile(); val store = VoicePackageStore(root)
        try {
            for (key in listOf("api_key", "rightsConfirmed")) {
                val f = files(); val metadata = JSONObject(f.getValue("voz.json").toString(Charsets.UTF_8))
                if (key == "api_key") metadata.put(key, "dato no permitido") else metadata.put(key, false)
                f["voz.json"] = metadata.toString().toByteArray()
                assertTrue(runCatching { store.install(zip(f).inputStream()) }.isFailure)
            }
        } finally { root.deleteRecursively() }
    }
    @Test fun `diccionario no inventa pronunciaciones de palabras desconocidas`() {
        val root = Files.createTempDirectory("lexicon-").toFile()
        try {
            val f = files(); val a = File(root, "lexicon.tsv").apply { writeBytes(f.getValue("lexicon.tsv")) }; val b = File(root, "tokens.txt").apply { writeBytes(f.getValue("tokens.txt")) }
            assertArrayEquals("hab".codePoints().toArray(), PronunciationLexicon.load(a, b).phonemes("HOLA"))
            assertTrue(runCatching { PronunciationLexicon.load(a, b).phonemes("desconocida") }.isFailure)
            a.writeText("hola\t☃\n"); assertTrue(runCatching { PronunciationLexicon.load(a, b) }.isFailure)
        } finally { root.deleteRecursively() }
    }
    @Test fun `protobuf truncado no acredita compatibilidad`() {
        val root = Files.createTempDirectory("onnx-").toFile()
        try {
            val file = File(root, "model.onnx")
            listOf(byteArrayOf(), ByteArray(500), structuralOnnx().dropLast(4).toByteArray()).forEach { file.writeBytes(it); assertTrue(runCatching { OnnxMetadata.validate(file) }.isFailure) }
        } finally { root.deleteRecursively() }
    }
}
