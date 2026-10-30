package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoiceRegressionTest {
    @Test fun enrutadorEjecutaLasOrdenesYDejaLasPreguntasAlProveedor() {
        val commands = mutableListOf<LocalVoiceCommand>(); val web = mutableListOf<WebAction>()
        val router = LocalTurnRouter({ commands.add(it) }, { web.add(it) })
        val music = listOf("Pon música", "Ponme música", "Pon música en Spotify", "Abre Spotify", "Pon Spotify", "Reproduce música en Spotify",
            "Pausa Spotify", "Continúa la música", "Siguiente canción", "Canción anterior")
        music.forEach { assertTrue(it, router.dispatch(it, "Jarvis", ListeningOptions(), .98f)) }
        assertEquals(music.size, commands.size)
        listOf("Abre Google", "Busca en Google", "Abre el navegador", "Busca en Google qué tiempo hará en Puigcerdà").forEach {
            assertTrue(router.dispatch(it, "Jarvis", ListeningOptions()))
        }
        assertEquals(4, web.size)
        listOf("Abre ChatGPT", "Abre GPT Live", "Para", "Termina", "Deja de escuchar").forEach { assertTrue(router.dispatch(it, "Jarvis", ListeningOptions())) }
        listOf("¿Qué es Spotify?", "¿Cómo funciona Google?", "¿Qué música me recomiendas?", "No pongas Spotify", "Para ir a Barcelona").forEach {
            assertFalse(router.dispatch(it, "Jarvis", ListeningOptions()))
        }
        assertEquals(music.size + 5, commands.size)
    }
    @Test fun ordenDudosaPideRepetirSinEjecutarNiEnviarAlModelo() {
        val router = LocalTurnRouter({ error("No ejecutar") }, { error("No abrir") })
        for (phrase in listOf("Pon música", "Abre Google", "Pon música en Spotify por ahí")) {
            try {
                if (!router.dispatch(phrase, "Jarvis", ListeningOptions(), .4f)) fail("No enviar a Groq")
                fail("Debe pedir repetición")
            } catch (_: RepeatLocalCommand) { }
        }
    }
    @Test fun automaticoNoImponeVoskLigeroSiHayDictadoLocalAndroidEnContinuo() {
        assertEquals(RecognitionEngine.ANDROID_LOCAL, RecognitionPolicy.engine(DictationSource.AUTO, false, true, true))
        assertEquals(RecognitionEngine.VOSK, RecognitionPolicy.engine(DictationSource.VOSK, true, true, true))
    }
    @Test fun avisoCorrectoDesactivadoLentoBloqueadoBluetoothYFocoNuncaRevocanLaEscucha() = runBlocking {
        val cases: List<Pair<Boolean, suspend () -> Unit>> = listOf(
            true to {}, false to { error("No debe reproducirse") }, true to { delay(1000) },
            true to { awaitCancellation() }, true to { throw IllegalStateException("Bluetooth no disponible") },
            true to { throw IllegalStateException("Foco denegado") }, true to { withTimeout(1) { awaitCancellation() } })
        for ((enabled, play) in cases) {
            val session = WakeSessionPolicy().apply { authorize() }
            val token = requireNotNull(session.beginListening())
            assertTrue(session.ready(token)); assertTrue(session.detected(token))
            val result = OptionalConfirmation.run(enabled, 20, play)
            assertTrue(session.authorized)
            assertNotEquals(ListeningPhase.SUSPENDED, session.phase)
            session.preparing()
            session.onVoice(true, false, false)
            assertEquals(ListeningPhase.CONVERSATION, session.phase)
            assertNotNull(result)
        }
    }
    @Test fun cancelarLaSesionDuranteAvisoLiberaRecursosYNoIniciaOtraCaptura() = runBlocking {
        val entered = CompletableDeferred<Unit>(); var closed = false; var captured = false
        val job = launch {
            OptionalConfirmation.run(true) {
                try { entered.complete(Unit); awaitCancellation() } finally { closed = true }
            }
            captured = true
        }
        entered.await(); job.cancelAndJoin()
        assertTrue(closed); assertFalse(captured)
    }
    @Test fun markdownConservaPalabrasNumerosPreguntasYLiterales() {
        assertEquals("Hola", TtsText.clean("**Hola**"))
        assertEquals("Hola", TtsText.clean("**Hola"))
        assertEquals("Espera...", TtsText.clean("*Espera...*"))
        assertEquals("Pregunta\n3,14 °C y 25 km/h. ¿Sí? ¡Claro!", TtsText.clean("# Pregunta\n**3,14 °C** y 25 km/h. ¿Sí? ¡Claro!"))
        assertEquals("Consulta Google.", TtsText.clean("Consulta [Google](https://google.com)."))
        assertEquals("Uno\nDos\n3. Tres", TtsText.clean("- Uno\n* Dos\n3. Tres"))
        assertTrue(TtsText.clean("Símbolo \\* y `*`.").contains("asterisco"))
        assertEquals("2 por 3 = 6", TtsText.clean("2*3 = 6"))
        assertEquals("val n = 42", TtsText.clean("```kotlin\nval n = 42\n```"))
        assertEquals("Puigcerdà y Esterri d'Àneu", TtsText.clean("__Puigcerdà__ y *Esterri d'Àneu*"))
    }
    @Test fun streamingConMarkdownPartidoConservaChatYEntregaTextoLimpioAlMotor() = runBlocking {
        val full = "**Hola**. *Espera...* Consulta [Google](https://google.com).\n- Temperatura: **3,14 °C**."
        for (remote in listOf(false, true)) for (width in listOf(1, 2, 7, 100)) {
            val shown = StringBuilder(); val heard = mutableListOf<String>()
            val result = StreamSpeechPipeline().run(remote,
                produce = { emit -> full.chunked(width).forEach { emit(it) }; full }, text = { shown.append(it) },
                synthesize = { heard += it; PcmAudio(ByteArray(2), 16000) },
                play = { for (audio in it) Unit }, failed = { throw it })
            assertEquals(full, result); assertEquals(full, shown.toString())
            assertFalse(heard.joinToString().contains('*'))
            assertTrue(heard.joinToString().contains("Hola")); assertTrue(heard.joinToString().contains("3,14 °C"))
            assertFalse(heard.joinToString().contains("https://"))
        }
    }
}
