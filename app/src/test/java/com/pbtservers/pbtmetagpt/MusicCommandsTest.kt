package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class MusicCommandsTest {
    @Test fun gramaticaMusicalExigeConfianzaAltaYLaIntervencionCompletaSinNegacion() {
        val open = CommandHypothesis("pon spotify", .4f, 1.0, 2.0)
        val candidate = open.copy(confidence = .98f)
        val bounds = SpeechBounds(1.0, 2.0)
        assertTrue(MusicCommandGrammar.accepts(open, candidate, bounds))
        assertFalse(MusicCommandGrammar.accepts(open, candidate.copy(confidence = .92f), bounds))
        assertFalse(MusicCommandGrammar.accepts(open, candidate.copy(confidence = Float.NaN), bounds))
        assertFalse(MusicCommandGrammar.accepts(open.copy(text = ""), candidate, bounds))
        assertFalse(MusicCommandGrammar.accepts(open.copy(text = "no pon spotify"), candidate, bounds))
        assertFalse(MusicCommandGrammar.accepts(open.copy(text = "explica pon spotify"), candidate, bounds))
        assertFalse(MusicCommandGrammar.accepts(open, candidate, SpeechBounds(.7, 2.0)))
        assertFalse(MusicCommandGrammar.accepts(open, candidate, SpeechBounds(1.0, 2.4)))
    }
    @Test fun ordenesDeMusicaCompletasYNombreDelAsistente() {
        val cases = mapOf("Pon Spotify" to MusicAction.PLAY, "Dale al play" to MusicAction.PLAY,
            "Reproduce música" to MusicAction.PLAY, "Pausa la música" to MusicAction.PAUSE,
            "Siguiente canción" to MusicAction.NEXT, "Cambia de canción" to MusicAction.NEXT,
            "Canción anterior" to MusicAction.PREVIOUS)
        cases.forEach { (phrase, action) ->
            assertEquals(action, MusicCommands.parse(phrase))
            assertEquals(MusicCommands.command(action), VoiceCommands.parse("Jarvis, $phrase", "Jarvis", ListeningOptions()))
        }
    }
    @Test fun mencionesNegacionesYPreguntasNoControlanLaMusica() {
        listOf("No pongas Spotify", "No reproduce música", "Cómo cambia de canción", "Dijo siguiente canción",
            "La siguiente canción me gusta", "Spotify", "Play", "Para").forEach {
            assertNull(it, MusicCommands.parse(it))
        }
        assertEquals(LocalVoiceCommand.INTERRUPT, VoiceCommands.parse("Para", "Jarvis", ListeningOptions()))
        assertEquals(LocalVoiceCommand.END, VoiceCommands.parse("Termina", "Jarvis", ListeningOptions()))
        assertEquals(LocalVoiceCommand.STOP_LISTENING, VoiceCommands.parse("Deja de escuchar", "Jarvis", ListeningOptions()))
    }
    @Test fun ordenDeMusicaNoPuedeUsarseComoFraseDeFinalizacion() {
        assertTrue(runCatching { ListeningOptions(endPhrase = "siguiente canción").validated() }.isFailure)
        assertEquals("fin de sesión", ListeningOptions(endPhrase = "fin de sesión").validated().endPhrase)
    }
    @Test fun controlLocalRearmaSoloLaEsperaYaAutorizada() {
        val policy = WakeSessionPolicy()
        assertFalse(policy.localCommandFinished())
        policy.authorize()
        val token = requireNotNull(policy.beginListening())
        assertTrue(policy.ready(token)); assertTrue(policy.detected(token))
        assertTrue(policy.localCommandFinished())
        assertFalse(policy.detected(token)); assertFalse(policy.localCommandFinished())
        val next = requireNotNull(policy.beginListening())
        assertTrue(policy.ready(next))
        policy.suspendListening()
        assertFalse(policy.localCommandFinished()); assertNull(policy.beginListening())
    }
}
