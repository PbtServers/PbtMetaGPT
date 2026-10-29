package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class SpainVoiceCatalogTest {
    @Test fun sharvardComparteModeloYUsaHablantesDistintosConContratoFijo() {
        val male = SpainVoiceCatalog.voice("sharvard_m")
        val female = SpainVoiceCatalog.voice("sharvard_f")
        assertEquals(male.packageId, female.packageId)
        assertEquals(0L, male.speakerId)
        assertEquals(1L, female.speakerId)
        assertEquals(SpainVoiceCatalog.spec(male.id), SpainVoiceCatalog.spec(female.id))
        assertEquals(3, SpainVoiceCatalog.voices.size)
        assertTrue(runCatching { SpainVoiceCatalog.voice("voz-inventada") }.isFailure)
    }
    @Test fun usaIndicesPiperYDistincionEspanolaSinConfundirlosConKokoro() {
        assertEquals(126L, SpainVoiceCatalog.phonemes['θ'.code])
        assertEquals(120L, SpainVoiceCatalog.phonemes['ˈ'.code])
        assertEquals(152, SpainVoiceCatalog.phonemes.size)
        assertArrayEquals(longArrayOf(1, 14, 0, 126, 0, 2), SpainVoiceCatalog.ids("aθ"))
        assertTrue(runCatching { SpainVoiceCatalog.ids("😀") }.isFailure)
    }
}
