package com.pbtservers.pbtmetagpt

data class SpainVoice(val id: String, val name: String, val packageId: String, val speakerId: Long? = null)

/** Voces publicadas como es_ES. Sharvard comparte pesos entre sus dos hablantes originales. */
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
    const val sharvardPackage = "piper-es_ES-sharvard-medium"
    const val sharvardRevision = "5041a7a49ae5b3a0f30e6837769eb03fdbb231bd/es/es_ES/sharvard/medium"
    val sharvardResource = KokoroResource("es_ES-sharvard-medium.onnx", 76733615,
        "40febfb1679c69a4505ff311dc136e121e3419a13a290ef264fdf43ddedd0fb1")
    val sharvardConfig = KokoroResource("es_ES-sharvard-medium.onnx.json", 4903,
        "7438c9b699c72b0c3388dae1b68d3f364dc66a2150fe554a1c11f03372957b2c")
    val voices = listOf(SpainVoice("davefx", "Davefx", packageId),
        SpainVoice("sharvard_m", "Sharvard · masculina", sharvardPackage, 0),
        SpainVoice("sharvard_f", "Sharvard · femenina", sharvardPackage, 1))
    fun voice(id: String) = requireNotNull(voices.singleOrNull { it.id == id }) { "Voz de España no compatible." }
    fun spec(id: String): NeuralVoiceSpec = if (voice(id).packageId == packageId) spec else NeuralVoiceSpec(
        sharvardPackage, "https://huggingface.co/rhasspy/piper-voices/resolve/", sharvardRevision,
        listOf(sharvardResource, sharvardConfig, KokoroResource("MODEL_CARD", 392,
            "37655314d08fe8a0900abc628d3bcbee48ffbdd8fad9306b89525a0ada20fefc")),
        phonemes.entries.joinToString("\n") { "${it.key.toChar()} ${it.value}" }, 300)
    fun ids(ipa: String, vocabulary: Map<Int, Long> = phonemes): LongArray {
        val ids = mutableListOf(1L)
        ipa.codePoints().forEach { code ->
            ids += requireNotNull(vocabulary[code]) { "Fonema no compatible con el modelo Piper seleccionado." }
            ids += 0L
        }
        ids += 2L
        require(ids.size in 3..1800) { "Fragmento demasiado largo para Piper." }
        return ids.toLongArray()
    }
}
