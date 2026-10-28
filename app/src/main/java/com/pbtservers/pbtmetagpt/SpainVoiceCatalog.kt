package com.pbtservers.pbtmetagpt

/** Modelo publicado como es_ES (España), dataset CC0; no se infiere el acento de una etiqueta genérica es. */
object SpainVoiceCatalog {
    const val packageId = "piper-es_ES-davefx-medium"
    val resource = KokoroResource("es_ES-davefx-medium.onnx", 63201294,
        "6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917")
    // Contrato de phoneme_id_map del modelo Davefx v1.0.0, no una tabla Kokoro.
    val phonemes: Map<Int, Long> = buildMap {
        val ascii = "_^$ !'(),-.:;?abcdefhijklmnopqrstuvwxyz"
        ascii.forEachIndexed { index, char -> put(char.code, index.toLong()) }
        val ipa = "æçðøħŋœǀǁǂǃɐɑɒɓɔɕɖɗɘəɚɛɜɞɟɠɡɢɣɤɥɦɧɨɪɫɬɭɮɯɰɱɲɳɴɵɶɸɹɺɻɽɾʀʁʂʃʄʈʉʊʋʌʍʎʏʐʑʒʔʕʘʙʛʜʝʟʡʢʲˈˌːˑ˞βθχᵻⱱ0123456789̧̪̯̩̃ʰˤε↓#\"↑"
        ipa.forEachIndexed { index, char -> put(char.code, (39 + index).toLong()) }
    }
    val spec get() = NeuralVoiceSpec(packageId, "https://huggingface.co/rhasspy/piper-voices/resolve/",
        "v1.0.0/es/es_ES/davefx/medium", listOf(resource),
        phonemes.entries.joinToString("\n") { "${it.key.toChar()} ${it.value}" }, 256)
    fun ids(ipa: String): LongArray {
        val ids = mutableListOf(1L)
        ipa.codePoints().forEach { code ->
            ids += requireNotNull(phonemes[code]) { "Fonema no compatible con Davefx." }
            ids += 0L
        }
        ids += 2L
        require(ids.size in 3..1800) { "Fragmento demasiado largo para Piper." }
        return ids.toLongArray()
    }
}
