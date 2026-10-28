package com.pbtservers.pbtmetagpt

/** eSpeak solo calcula IPA; todo el PCM lo genera Kokoro neuronal. */
@androidx.annotation.Keep
object SpanishPhonemizer {
    init { System.loadLibrary("pbt_phonemes") }
    external fun convert(directory: String, text: String): String
    external fun release()
}

object LocalPhonemes {
    @Volatile private var available = false
    @Synchronized fun convert(directory: String, text: String): String = try {
        SpanishPhonemizer.convert(directory, text).also { available = true }
    } catch (_: LinkageError) { throw HostedFailure("El fonemizador local no está disponible en esta instalación. Revisa la APK y su arquitectura.") }
    @Synchronized fun release() {
        if (available) try { SpanishPhonemizer.release() } finally { available = false }
    }
}
