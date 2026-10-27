package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SpeechTaskQueueTest {
    private val profile = AssistantProfile(name = "Jarvis", ttsEngine = "android")
    @Test fun `interrumpir descarta audio tardio incluso de motor que no coopera`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val pending = CompletableDeferred<PcmAudio>(); var played = 0; var completed = 0; var flushed = 0
        val queue = SpeechTaskQueue(scope, { _, _ -> withContext(NonCancellable) { pending.await() } }, { played++ }, { flushed++ }, {}, { fail(it) })
        try {
            queue.speak(profile, "hola") { completed++ }
            queue.stop(); pending.complete(PcmAudio(byteArrayOf(0, 1), 24000)); yield()
            assertEquals(0, played); assertEquals(0, completed); assertTrue(flushed >= 2)
        } finally { scope.cancel() }
    }
    @Test fun `nuevo motor cancela y vacia salida anterior sin reproduccion simultanea`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var active = 0; var maximum = 0; var flushed = 0
        val queue = SpeechTaskQueue(scope, { _, _ -> PcmAudio(byteArrayOf(0, 1), 24000) }, {
            active++; maximum = maxOf(maximum, active)
            try { awaitCancellation() } finally { active-- }
        }, { flushed++ }, {}, { fail(it) })
        try {
            queue.speak(profile, "primera"); assertEquals(1, active)
            queue.speak(profile.copy(ttsEngine = "external"), "segunda"); assertEquals(1, active)
            queue.stop(); assertEquals(0, active); assertEquals(1, maximum); assertTrue(flushed >= 3)
        } finally { scope.cancel() }
    }
    @Test fun `error de proveedor no expone su cuerpo ni sintetiza con otra voz`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var message = ""; var rendered = 0; var completed = false
        val secret = "sk-" + "a".repeat(40)
        val queue = SpeechTaskQueue(scope, { _, _ -> rendered++; throw IllegalStateException(secret) }, { fail("No debe reproducir") }, {}, {}, { message = it })
        try {
            queue.speak(profile, "hola") { completed = true }
            assertEquals(1, rendered); assertTrue(completed); assertFalse(message.contains(secret)); assertTrue(message.contains("No se ha cambiado"))
        } finally { scope.cancel() }
    }
}
