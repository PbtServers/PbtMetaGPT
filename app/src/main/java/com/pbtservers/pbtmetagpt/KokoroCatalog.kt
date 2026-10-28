package com.pbtservers.pbtmetagpt

import java.io.File
import java.security.MessageDigest

data class KokoroResource(val name: String, val bytes: Long, val sha256: String)
data class NeuralVoiceSpec(val id: String, val source: String, val revision: String,
    val resources: List<KokoroResource>, val tokens: String, val freeMiB: Int)

object KokoroCatalog {
    const val revision = "f7b96bb6bef5c5da4d3aa4f4e0498fbbf62dc78b"
    const val packageId = "kokoro-v1.0"
    const val source = "https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/resolve/"
    val resources = listOf(
        KokoroResource("model.onnx", 325560556, "b40f62b166ac8164b0627ef48a0b358eda0985e272fb03ef5252e7206305da11"),
        KokoroResource("voices.bin", 28200960, "1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29"))
    val voices = linkedMapOf("ef_dora" to 28, "em_alex" to 29, "em_santa" to 53)
    fun name(id: String) = when (id) { "ef_dora" -> "Dora"; "em_alex" -> "Alex"; "em_santa" -> "Santa"; else -> id }
    // Índices del vocabulario Kokoro v1.0. Este subconjunto cubre el IPA español.
    val tokens = "; 1\n: 2\n, 3\n. 4\n! 5\n? 6\n\u2014 9\n\u2026 10\n\" 11\n( 12\n) 13\n\u201c 14\n\u201d 15\n  16\n\u0303 17\n\u02a3 18\n\u02a5 19\n\u02a6 20\n\u02a8 21\n\u1d5d 22\n\uab67 23\nA 24\nI 25\nO 31\nQ 33\nS 35\nT 36\nW 39\nY 41\n\u1d4a 42\na 43\nb 44\nc 45\nd 46\ne 47\nf 48\nh 50\ni 51\nj 52\nk 53\nl 54\nm 55\nn 56\no 57\np 58\nq 59\nr 60\ns 61\nt 62\nu 63\nv 64\nw 65\nx 66\ny 67\nz 68\n\u0251 69\n\u0250 70\n\u0252 71\n\u00e6 72\n\u03b2 75\n\u0254 76\n\u0255 77\n\u00e7 78\n\u0256 80\n\u00f0 81\n\u02a4 82\n\u0259 83\n\u025a 85\n\u025b 86\n\u025c 87\n\u025f 90\n\u0261 92\n\u0265 99\n\u0268 101\n\u026a 102\n\u029d 103\n\u026f 110\n\u0270 111\n\u014b 112\n\u0273 113\n\u0272 114\n\u0274 115\n\u00f8 116\n\u0278 118\n\u03b8 119\n\u0153 120\n\u0279 123\n\u027e 125\n\u027b 126\n\u0281 128\n\u027d 129\n\u0282 130\n\u0283 131\n\u0288 132\n\u02a7 133\n\u028a 135\n\u028b 136\n\u028c 138\n\u0263 139\n\u0264 140\n\u03c7 142\n\u028e 143\n\u0292 147\n\u0294 148\n\u02c8 156\n\u02cc 157\n\u02d0 158\n\u02b0 162\n\u02b2 164\n\u2193 169\n\u2192 171\n\u2197 172\n\u2198 173\n\u1d7b 177\n"
    fun digest(file: File): String = file.inputStream().use { input ->
        val hash = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(65536)
        while (true) { val n = input.read(buffer); if (n < 0) break; hash.update(buffer, 0, n) }
        hash.digest().joinToString("") { "%02x".format(it) }
    }
    fun verify(file: File, resource: KokoroResource) = file.length() == resource.bytes && digest(file) == resource.sha256
    val spec get() = NeuralVoiceSpec(packageId, source, revision, resources, tokens, 750)
}
