package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class SpanishSpeechTextTest {
    @Test fun deletreaSiglasEnRespuestasSinCambiarLaFraseNiLaNegacion() {
        assertEquals("Abre chat ge pe te.", SpanishSpeechText.pronunciation("Abre ChatGPT."))
        assertEquals("Inicia chat ge pe te en voz.", SpanishSpeechText.pronunciation("Inicia ChatGPT en voz."))
        assertEquals("No abras ge pe te Live.", SpanishSpeechText.pronunciation("No abras GPT Live."))
    }
    @Test fun conservaPalabrasOrdinariasYControlesSinCambiarSubcadenas() {
        for (text in listOf("Para.", "Termina.", "Deja de escuchar.", "En Zaragoza hay cinco plazas.", "gptional"))
            assertEquals(text, SpanishSpeechText.pronunciation(text))
    }
}
