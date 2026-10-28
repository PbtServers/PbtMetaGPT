package com.pbtservers.pbtmetagpt

import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files

class KokoroCatalogTest {
    @Test fun sha256RechazaCorrupcionAunqueSeConserveElTamano() {
        val file = Files.createTempFile("kokoro-integridad", ".bin").toFile()
        try {
            val expected = KokoroResource("fixture", 3, "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
            file.writeText("abc"); assertTrue(KokoroCatalog.verify(file, expected))
            file.writeText("abd"); assertFalse(KokoroCatalog.verify(file, expected))
            file.writeText("ab"); assertFalse(KokoroCatalog.verify(file, expected))
        } finally { file.delete() }
    }
    @Test fun vocabularioIpaConservaAcentosFoneticosYLosIndicesDelModelo() {
        val entries = KokoroCatalog.tokens.lineSequence().filter { it.isNotBlank() }
            .map { it.substringBeforeLast(' ') to it.substringAfterLast(' ').toInt() }.toList()
        assertEquals(entries.size, entries.map { it.first }.distinct().size)
        assertEquals(entries.size, entries.map { it.second }.distinct().size)
        val vocabulary = entries.toMap()
        assertEquals(156, vocabulary["ˈ"]); assertEquals(119, vocabulary["θ"])
        assertEquals(114, vocabulary["ɲ"]); assertEquals(92, vocabulary["ɡ"])
        assertEquals(16, vocabulary[" "])
    }
    @Test fun conservaPuntuacionSinRomperDecimalesAntesDeFonemizar() {
        val chunks = mutableListOf<String>()
        val ipa = KokoroText.phonemes("Son 3.14 euros, ¿de acuerdo?") { chunks += it; "ola" }
        assertEquals(listOf("Son 3.14 euros,", " ¿de acuerdo?"), chunks)
        assertEquals("ola, ola?", ipa)
    }
}
