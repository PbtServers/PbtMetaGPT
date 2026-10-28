package com.pbtservers.pbtmetagpt

/** Conversión IEEE-754 binary32/binary16 usando únicamente bits sin @HalfFloat Android. */
object RvcHalfBits {
    fun encode(value: Float): Short {
        val raw = java.lang.Float.floatToRawIntBits(value)
        val sign = (raw ushr 16) and 0x8000
        val exponent = (raw ushr 23) and 255
        val mantissa = raw and 0x7fffff
        if (exponent == 255) {
            // NaN canónico quieto o infinito, conservando el signo.
            return (sign or if (mantissa != 0) 0x7e00 else 0x7c00).toShort()
        }
        var halfExp = exponent - 127 + 15
        if (halfExp >= 31) return (sign or 0x7c00).toShort()
        if (halfExp < -10) return sign.toShort()

        if (halfExp <= 0) {
            val normalized = mantissa or 0x800000
            val shift = 14 - halfExp
            var rounded = normalized ushr shift
            val remains = normalized and ((1 shl shift) - 1)
            val halfway = 1 shl (shift - 1)
            if (remains > halfway || (remains == halfway && rounded and 1 != 0)) rounded++
            return (sign or rounded).toShort()
        }
        var rounded = mantissa ushr 13
        val remainder = mantissa and 0x1fff
        if (remainder > 0x1000 || (remainder == 0x1000 && rounded and 1 != 0)) rounded++
        if (rounded == 0x400) {
            rounded = 0
            halfExp++
            if (halfExp >= 31) return (sign or 0x7c00).toShort()
        }
        return (sign or (halfExp shl 10) or rounded).toShort()
    }

    fun decode(half: Short): Float {
        val bits = half.toInt() and 0xffff
        val sign = (bits and 0x8000) shl 16
        val exponent = (bits ushr 10) and 31
        var fraction = bits and 0x3ff
        val resultBits = when (exponent) {
            0 -> {
                if (fraction == 0) sign
                else {
                    var power = -14
                    while (fraction and 0x400 == 0) {
                        fraction = fraction shl 1
                        power--
                    }
                    fraction = fraction and 0x3ff
                    sign or ((power + 127) shl 23) or (fraction shl 13)
                }
            }
            31 -> sign or 0x7f800000 or (fraction shl 13)
            else -> sign or ((exponent - 15 + 127) shl 23) or (fraction shl 13)
        }
        return java.lang.Float.intBitsToFloat(resultBits)
    }
}
