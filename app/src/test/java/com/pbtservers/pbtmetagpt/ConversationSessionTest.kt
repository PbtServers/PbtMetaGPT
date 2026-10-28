package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ConversationSessionTest {
    private val options = ListeningOptions()
    @Test fun activacionAutorizaUnaConversacionYLosTurnosVuelvenALaEscucha() {
        val session = ConversationSession()
        session.start(spoken = true)
        repeat(5) {
            assertTrue(session.move(SessionPhase.PROCESSING))
            assertTrue(session.move(SessionPhase.SPEAKING))
            assertTrue(session.completed(session.epoch, continuous = true))
            assertEquals(SessionPhase.FOLLOW_UP_LISTENING, session.phase)
        }
        assertTrue(session.authorized)
    }
    @Test fun cincoPreguntasConservanContextoEInstruccionesSeparadas() {
        val history = ConversationHistory()
        repeat(5) { history.user("Pregunta $it"); history.assistant("Respuesta $it") }
        assertEquals(10, history.snapshot().size)
        assertEquals("Pregunta 0", history.snapshot().first().second)
        assertEquals("Respuesta 4", history.snapshot().last().second)
    }
    @Test fun contextoAcotadoNoComienzaConRespuestaHuerfana() {
        val history = ConversationHistory(maxMessages = 4, maxCharacters = 40)
        repeat(20) { history.user("Pregunta $it"); history.assistant("Respuesta $it") }
        assertTrue(history.snapshot().size <= 4)
        assertTrue(history.snapshot().sumOf { it.second.length } <= 40)
        assertEquals("user", history.snapshot().first().first)
    }
    @Test fun unaInterrupcionConservaElUsuarioSinConfirmarAsistenteIncompleto() {
        val history = ConversationHistory()
        history.user("Primera"); history.assistant("Completa"); history.user("Segunda")
        val session = ConversationSession().apply { start(true); move(SessionPhase.SPEAKING) }
        val old = session.epoch
        session.interrupt()
        assertFalse(session.accepts(old))
        assertFalse(session.completed(old, true))
        assertTrue(session.authorized)
        assertTrue(session.move(SessionPhase.FOLLOW_UP_LISTENING))
        assertEquals(listOf("user", "assistant", "user"), history.snapshot().map { it.first })
    }
    @Test fun paraRepetidoInvalidaTodasLasGeneraciones() {
        val session = ConversationSession().apply { start(true) }
        val epochs = (0..50).map { session.interrupt() }
        assertTrue(epochs.dropLast(1).none(session::accepts))
        assertTrue(session.accepts(epochs.last()))
        assertTrue(session.authorized)
    }
    @Test fun finalizacionDuranteSintesisImpideRearmarUnaSesionAntigua() {
        val session = ConversationSession().apply { start(true); move(SessionPhase.PROCESSING) }
        val old = session.epoch
        session.stop()
        assertFalse(session.completed(old, true)); assertFalse(session.move(SessionPhase.SPEAKING, old))
        assertEquals(SessionPhase.STOPPED, session.phase)
    }
    @Test fun perdidaDeAudioPausaYRequiereReanudacionExplicita() {
        val session = ConversationSession().apply { start(true) }
        val old = session.epoch
        session.suspendSession()
        assertTrue(session.authorized)
        assertFalse(session.completed(old, true)); assertFalse(session.move(SessionPhase.USER_LISTENING))
        session.resume()
        assertEquals(SessionPhase.FOLLOW_UP_LISTENING, session.phase)
        assertFalse(session.accepts(old))
    }
    @Test fun textoManualYModoDeUnTurnoNoInicianCapturaAutomatica() {
        val session = ConversationSession().apply { start(false) }
        assertFalse(session.completed(session.epoch, true))
        session.start(true)
        assertFalse(session.completed(session.epoch, false))
    }
    @Test fun paraDebeSerUnEnunciadoCompleto() {
        assertEquals(LocalVoiceCommand.INTERRUPT, VoiceCommands.parse("¡Para!", "Jarvis", options))
        assertEquals(LocalVoiceCommand.INTERRUPT, VoiceCommands.parse("Jarvis, para", "Jarvis", options))
        listOf("Esto es para mañana", "para mañana", "no para", "prepara la cena", "Para y dime otra cosa").forEach {
            assertNull(it, VoiceCommands.parse(it, "Jarvis", options))
        }
    }
    @Test fun cierreYDetencionTienenIntencionesDistintas() {
        listOf("Termina", "Termina la conversación", "Jarvis, termina").forEach {
            assertEquals(LocalVoiceCommand.END, VoiceCommands.parse(it, "Jarvis", options))
        }
        assertEquals(LocalVoiceCommand.STOP_LISTENING, VoiceCommands.parse("Deja de escuchar", "Jarvis", options))
        assertNull(VoiceCommands.parse("Me dijo hasta luego", "Jarvis", options.copy(goodbye = true)))
        assertNull(VoiceCommands.parse("Hasta luego", "Jarvis", options))
        assertEquals(LocalVoiceCommand.END, VoiceCommands.parse("Hasta luego", "Jarvis", options.copy(goodbye = true)))
    }
    @Test fun aperturaChatGptSoloPorOrdenCompleta() {
        listOf("Abre GPT Live", "Inicia GPT Live", "Abre ChatGPT en voz", "Quiero hablar con ChatGPT", "Jarvis, abre GPT Live").forEach {
            assertEquals(LocalVoiceCommand.CHATGPT, VoiceCommands.parse(it, "Jarvis", options))
        }
        assertNull(VoiceCommands.parse("Explica cómo abre GPT Live", "Jarvis", options))
    }
    @Test fun fraseDeFinalizacionPersonalizadaSeNormaliza() {
        assertEquals(LocalVoiceCommand.END, VoiceCommands.parse("Luna, fin de sesión", "Luna", options.copy(endPhrase = "Fin de sesión")))
    }
    @Test fun ecoYVentanaDeProteccionImpidenOrdenes() {
        val gate = InterruptionGate()
        assertNull(gate.accept("para", 1f, 2000, 1000, false, "Jarvis", options))
        assertNull(gate.accept("para", 1f, 2000, 2500, true, "Jarvis", options))
        assertNotNull(gate.accept("para", 1f, 3000, 2500, true, "Jarvis", options))
        assertNull(gate.accept("para", 1f, 3100, 2500, true, "Jarvis", options))
    }
    @Test fun confianzaYSensibilidadSeAplicanSinForzarPalabras() {
        assertNull(InterruptionGate().accept("para", 0.8f, 2000, 0, true, "", options.copy(sensitivity = 0f)))
        assertNotNull(InterruptionGate().accept("para", 0.8f, 2000, 0, true, "", options.copy(sensitivity = 1f)))
        assertNull(InterruptionGate().accept("para mañana", 1f, 2000, 0, true, "", options))
        assertNull(InterruptionGate().accept("para", Float.NaN, 2000, 0, true, "", options))
        assertNull(InterruptionGate().accept("para", 1f, 2000, 0, true, "", options.copy(recognizeStop = false)))
    }
    @Test fun silencioSinCaducidadNoCambiaLaAutorizacion() {
        val session = ConversationSession().apply { start(true) }
        assertEquals(0, options.inactivitySeconds)
        repeat(100) { assertTrue(session.completed(session.epoch, true)) }
        assertTrue(session.authorized)
    }
    @Test fun terminarRecuperaEsperaSoloConConsentimiento() {
        val wake = WakeSessionPolicy().apply { authorize() }
        wake.onVoice(true, false, false)
        assertTrue(wake.onVoice(false, false, false))
        wake.stop()
        assertFalse(wake.onVoice(false, false, false))
    }
    @Test fun resultadoNativoTardioNoLlegaAlReproductor() = runBlocking<Unit> {
        val synthesizing = CompletableDeferred<Unit>()
        val nativeFinished = CompletableDeferred<Unit>()
        val played = mutableListOf<PcmAudio>()
        val task = launch {
            StreamSpeechPipeline().run(false, produce = { emit -> emit("Una frase suficientemente larga para sintetizar."); "fin" }, text = {},
                synthesize = {
                    synthesizing.complete(Unit)
                    withContext(NonCancellable) { nativeFinished.await() }
                    PcmAudio(ByteArray(320), 16000)
                }, play = { audio -> for (pcm in audio) played += pcm }, failed = { throw it })
        }
        synthesizing.await(); task.cancel(); nativeFinished.complete(Unit); task.join()
        assertTrue(played.isEmpty())
    }
    @Test fun errorNativoTardioNoPublicaEstadoDeUnaGeneracionCancelada() = runBlocking<Unit> {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var errors = 0
        val task = launch {
            StreamSpeechPipeline().run(false, produce = { emit -> emit("Otra frase suficientemente larga para síntesis."); "fin" }, text = {},
                synthesize = {
                    started.complete(Unit)
                    withContext(NonCancellable) { release.await() }
                    throw IllegalStateException("Fallo nativo de prueba")
                }, play = { for (ignored in it) fail("No debe haber audio") }, failed = { errors++ })
        }
        started.await(); task.cancel(); release.complete(Unit); task.join()
        assertEquals(0, errors)
    }
}
