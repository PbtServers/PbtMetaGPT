package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VoiceRepairTest {
    @Test fun unaInterrupcionEsperaTambienAlDetectorAntesDePermitirOtraCaptura() = runBlocking {
        val response = Job()
        val microphone = Job()
        val released = drainAudioJobs(this, response, microphone)
        try {
            response.complete(); yield()
            assertFalse(released.isCompleted)
            assertNull(withTimeoutOrNull(20) { released.join(); true })
            microphone.complete()
            awaitAudioRelease(released, 1000)
            assertTrue(released.isCompleted)
        } finally { response.cancel(); microphone.cancel(); released.cancel() }
    }
    @Test fun elDictadoLocalDudosoNoEjecutaAccionesNiSeConfundeConPregunta() {
        val options = ListeningOptions()
        assertFalse(VoiceCommands.confident("pon música", .84f, "Jarvis", options))
        assertFalse(VoiceCommands.confident("Abre Google", Float.NaN, "Jarvis", options))
        assertFalse(VoiceCommands.confident("Busca en Google Barcelona", .4f, "Jarvis", options))
        assertTrue(VoiceCommands.confident("Pausa Spotify", .95f, "Jarvis", options))
        assertTrue(VoiceCommands.confident("Qué es Spotify", .5f, "Jarvis", options))
    }
    @Test fun finalizacionPrecisaEsAdaptativaParaUnaPrimeraPalabraSeguidaDePausa() {
        val vad = AdaptiveVad(endSilenceMs = 1700, adaptiveEnding = true)
        repeat(20) { vad.accept(ShortArray(320) { 2000 }) }
        repeat(99) { assertNotEquals(AdaptiveVad.Event.END, vad.accept(ShortArray(320))) }
        assertEquals(AdaptiveVad.Event.END, vad.accept(ShortArray(320)))
        val long = AdaptiveVad(endSilenceMs = 1700, adaptiveEnding = true)
        repeat(60) { long.accept(ShortArray(320) { 2000 }) }
        repeat(84) { assertNotEquals(AdaptiveVad.Event.END, long.accept(ShortArray(320))) }
        assertEquals(AdaptiveVad.Event.END, long.accept(ShortArray(320)))
    }
    @Test fun unErrorAnteriorNoRevocaUnaNuevaAutorizacionMientrasCargaElDetector() {
        val policy = WakeSessionPolicy().apply { authorize() }
        assertFalse(policy.onVoice(false, false, true))
        assertTrue(policy.authorized)
        val token = requireNotNull(policy.beginListening())
        assertTrue(policy.ready(token)); assertTrue(policy.detected(token))
        policy.preparing()
        assertFalse(policy.onVoice(false, false, false))
        assertTrue(policy.authorized)
        assertEquals(ListeningPhase.PREPARING, policy.phase)
        policy.onVoice(true, false, false)
        policy.preparing()
        assertEquals(ListeningPhase.CONVERSATION, policy.phase)
        assertTrue(policy.onVoice(false, false, false))
    }
    @Test fun callbacksDeCapturaAnterioresNoSuspendenUnaConversacionNiUnaNuevaAutorizacion() {
        val policy = WakeSessionPolicy().apply { authorize() }
        val old = requireNotNull(policy.beginListening())
        assertTrue(policy.captureCurrent(old))
        policy.ready(old); policy.detected(old)
        assertFalse(policy.captureCurrent(old))
        policy.preparing(); policy.onVoice(true, false, false)
        assertFalse(policy.captureCurrent(old))
        policy.stop(); policy.authorize()
        val next = requireNotNull(policy.beginListening())
        assertTrue(policy.captureCurrent(next)); assertFalse(policy.captureCurrent(old))
    }
    @Test fun unaLiberacionAtascadaProduceErrorYNoConcedeOtraCaptura() = runBlocking {
        val gate = MicrophoneGate()
        val lease = requireNotNull(gate.acquire(MicrophoneOwner.CONTINUOUS_LOCAL))
        val stuck = Job()
        try {
            try { awaitAudioRelease(stuck, 20); fail("Debe informar del timeout") } catch (_: HostedFailure) { }
            assertNull(gate.acquire(MicrophoneOwner.HOSTED_LOCAL))
            gate.release(lease); stuck.complete()
            awaitAudioRelease(stuck, 20)
            assertNotNull(gate.acquire(MicrophoneOwner.HOSTED_LOCAL))
        } finally { stuck.cancel() }
    }
    @Test fun cancelarLaEsperaNoOcultaLaCancelacionNiLiberaUnMicrofonoAjeno() = runBlocking {
        val stuck = Job()
        try {
            assertNull(withTimeoutOrNull(20) { awaitAudioRelease(stuck, 10000); true })
            assertFalse(stuck.isCompleted)
        } finally { stuck.cancel() }
    }
    @Test fun whisperAutorizadoSeRespetaEnContinuoYLaSeleccionLocalNoEnviaAudio() {
        assertEquals(RecognitionEngine.GROQ_WHISPER, RecognitionPolicy.engine(DictationSource.AUTO, true, true, true))
        assertEquals(RecognitionEngine.VOSK, RecognitionPolicy.engine(DictationSource.VOSK, true, true, true))
        assertEquals(RecognitionEngine.ANDROID_LOCAL, RecognitionPolicy.engine(DictationSource.ANDROID_LOCAL, false, true, true))
        assertEquals(RecognitionEngine.VOSK, RecognitionPolicy.engine(DictationSource.AUTO, false, false, true))
        assertEquals("whisper-large-v3", RecognitionPolicy.whisperModel(RecognitionMode.PRECISION))
        assertEquals("whisper-large-v3-turbo", RecognitionPolicy.whisperModel(RecognitionMode.SPEED))
        assertTrue(RecognitionPolicy.silenceMs(RecognitionMode.PRECISION) > RecognitionPolicy.silenceMs(RecognitionMode.SPEED))
    }
    @Test fun pausasNaturalesYPrimerasSilabasSeConservanEnElModoPreciso() {
        val vad = AdaptiveVad(endSilenceMs = RecognitionPolicy.silenceMs(RecognitionMode.PRECISION))
        val prefix = PcmPreRoll()
        repeat(10) { prefix.append(ShortArray(320) { 17 }) }
        repeat(3) { val frame = ShortArray(320) { 2000 }; prefix.append(frame); vad.accept(frame) }
        assertTrue(vad.started)
        assertEquals(17, prefix.snapshot().first().toInt())
        repeat(60) { assertNotEquals(AdaptiveVad.Event.END, vad.accept(ShortArray(320))) }
        assertEquals(AdaptiveVad.Event.VOICE, vad.accept(ShortArray(320) { 2000 }))
        repeat(84) { assertNotEquals(AdaptiveVad.Event.END, vad.accept(ShortArray(320))) }
        assertEquals(AdaptiveVad.Event.END, vad.accept(ShortArray(320)))
        prefix.clear(); assertTrue(prefix.snapshot().isEmpty())
    }
    @Test fun spotifyYGoogleSeEjecutanPorIntencionYLasPreguntasNoSeConviertenEnOrdenes() {
        listOf("Pon música", "Ponme música", "Pon algo de música", "Quiero escuchar música", "Abre Spotify", "Reproduce música en Spotify", "Continúa la música", "Ponme algo en Spotify").forEach {
            assertEquals(it, LocalVoiceCommand.MUSIC_PLAY, VoiceCommands.parse(it, "Jarvis", ListeningOptions()))
        }
        assertEquals(LocalVoiceCommand.MUSIC_PAUSE, VoiceCommands.parse("Para Spotify", "Jarvis", ListeningOptions()))
        assertEquals(LocalVoiceCommand.INTERRUPT, VoiceCommands.parse("Para", "Jarvis", ListeningOptions()))
        assertEquals(LocalVoiceCommand.MUSIC_NEXT, VoiceCommands.parse("Pon la siguiente", "Jarvis", ListeningOptions()))
        listOf("¿Qué es Spotify?", "¿Cómo funciona Google?", "¿Qué música me recomiendas?", "No pongas música", "Para ir a Puigcerdà").forEach {
            assertNull(it, VoiceCommands.parse(it, "Jarvis", ListeningOptions())); assertNull(WebCommands.parse(it))
        }
        assertEquals(WebDestination.GOOGLE, WebCommands.parse("Abre Google")?.destination)
        assertEquals(LocalVoiceCommand.SYSTEM_ASSISTANT, VoiceCommands.parse("Abre el asistente del teléfono", "Jarvis", ListeningOptions()))
        assertEquals(WebAction(WebDestination.SEARCH, "restaurantes de Barcelona"), WebCommands.parse("Busca en Google restaurantes de Barcelona"))
        assertEquals(WebAction(WebDestination.SEARCH, "qué tiempo hará en Puigcerdà"), WebCommands.parse("Busca en Google qué tiempo hará en Puigcerdà"))
        assertEquals(WebAction(WebDestination.SEARCH, "qué tiempo hará en Puigcerdà"), WebCommands.parse("Jarvis, busca en Google qué tiempo hará en Puigcerdà", "Jarvis"))
        assertNull(WebCommands.parse("¿Cómo busca Jarvis en Google?", "Jarvis"))
    }
    @Test fun diagnosticoAcotadoSoloContieneCategoriasYDuraciones() {
        var clock = 10L
        val events = VoiceTransitions { clock }
        repeat(100) { clock += 20; events.record(ListeningPhase.LOADING, TransitionReason.START, null, 15) }
        assertEquals(64, events.report().lines().size)
        assertTrue(events.report().contains("etapa_ms=20"))
        assertTrue(events.report().contains("mic=LIBRE"))
    }
}
