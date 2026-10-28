package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class RvcHalfBitsTest {
    @Test fun representaCerosYValoresIEEESinWideningAndroid() {
        assertEquals(0x0000, RvcHalfBits.encode(0f).toInt() and 0xffff)
        assertEquals(0x8000, RvcHalfBits.encode(-0f).toInt() and 0xffff)
        assertEquals(0x3c00, RvcHalfBits.encode(1f).toInt() and 0xffff)
        assertEquals(0xbc00, RvcHalfBits.encode(-1f).toInt() and 0xffff)
        assertEquals(0x7bff, RvcHalfBits.encode(65504f).toInt() and 0xffff)
        assertEquals(0x7c00, RvcHalfBits.encode(Float.POSITIVE_INFINITY).toInt() and 0xffff)
        assertEquals(0x7e00, RvcHalfBits.encode(Float.NaN).toInt() and 0xffff)
        assertEquals(1f, RvcHalfBits.decode(0x3c00.toShort()), 0f)
        assertEquals(-1f, RvcHalfBits.decode(0xbc00.toShort()), 0f)
        assertTrue(RvcHalfBits.decode(0x7e00.toShort()).isNaN())
    }

    @Test fun recuperaNormalesYSubnormalesSinPerderElSigno() {
        assertEquals(1.0f / 16777216f, RvcHalfBits.decode(1.toShort()), 0f)
        assertTrue(abs(RvcHalfBits.decode(0x0400.toShort()) - 1f / 16384f) < 1e-10)
        for (value in listOf(-100f, -0.5f, 0.003f, 0.1f, 1.5f, 20f, 123.125f)) {
            val decoded = RvcHalfBits.decode(RvcHalfBits.encode(value))
            assertTrue("Error de conversión FP16 para " + value, abs(decoded - value) < 0.05f)
        }
    }
}
