package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class RecognitionMetricsTest {
    @Test fun cuentaOmisionesSustitucionesEInsercionesSinConfundirlasConAciertos() {
        assertEquals(RecognitionErrors(3, 0, 1, 0), RecognitionMetrics.words("pon musica ahora", "pon ahora"))
        assertEquals(RecognitionErrors(2, 1, 0, 0), RecognitionMetrics.words("abre google", "abre gugel"))
        assertEquals(RecognitionErrors(2, 0, 0, 1), RecognitionMetrics.words("pon musica", "pon la musica"))
        assertEquals(RecognitionErrors(2, 0, 0, 0), RecognitionMetrics.words("¿Qué música?", "que musica"))
        assertEquals(.5, requireNotNull(RecognitionMetrics.words("abre google", "abre gugel").rate), .000001)
    }
    @Test fun caracteresYCasosVaciosNoProducenUnaPrecisionInventada() {
        assertEquals(RecognitionErrors(4, 1, 0, 0), RecognitionMetrics.characters("para", "pasa"))
        assertNull(RecognitionMetrics.words("", "hola").rate)
        assertEquals(1.0, requireNotNull(RecognitionMetrics.words("hola", "").rate), .0)
        assertEquals(RecognitionErrors(4, 1, 1, 1), RecognitionErrors(2, 1, 0, 1) + RecognitionErrors(2, 0, 1, 0))
    }
}
