package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class SpainVoiceCatalogTest {
    @Test fun usaIndicesPiperYDistincionEspanolaSinConfundirlosConKokoro() {
        assertEquals(126L, SpainVoiceCatalog.phonemes['θ'.code])
        assertEquals(120L, SpainVoiceCatalog.phonemes['ˈ'.code])
        assertEquals(152, SpainVoiceCatalog.phonemes.size)
        assertArrayEquals(longArrayOf(1, 14, 0, 126, 0, 2), SpainVoiceCatalog.ids("aθ"))
        assertTrue(runCatching { SpainVoiceCatalog.ids("😀") }.isFailure)
    }
}
