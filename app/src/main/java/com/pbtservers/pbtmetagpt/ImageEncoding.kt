package com.pbtservers.pbtmetagpt

import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream

internal object ImageEncoding {
    fun jpeg(bitmap: Bitmap): String {
        require(!bitmap.isRecycled && bitmap.width > 0 && bitmap.height > 0) { "La imagen ya no está disponible." }
        var edge = 512
        repeat(4) {
            val ratio = minOf(1f, edge.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * ratio).toInt()), maxOf(1, (bitmap.height * ratio).toInt()), true)
            val bytes = try {
                ByteArrayOutputStream().use { out ->
                    check(scaled.compress(Bitmap.CompressFormat.JPEG, 65, out))
                    out.toByteArray()
                }
            } finally { if (scaled !== bitmap) scaled.recycle() }
            // 42000 bytes de JPEG dejan margen para base64, pregunta y JSON en SCTP (<64 KiB).
            if (bytes.size <= 42_000) return Base64.encodeToString(bytes, Base64.NO_WRAP)
            edge /= 2
        }
        throw IllegalStateException("Imagen demasiado grande para el canal de datos.")
    }
}
