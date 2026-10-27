package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ErrorJournalTest {
    @get:Rule val directory = TemporaryFolder()
    @Test fun `no persiste mensajes causas identificadores ni URL`() {
        val journal = ErrorJournal(directory.root)
        val error = IllegalStateException("secreto-personal https://privado.invalid/?token=credencial", SecurityException("otra clave"))
        error.stackTrace = arrayOf(StackTraceElement("com.pbtservers.pbtmetagpt.RealtimeClient", "dato-privado", "RealtimeClient.kt", 123),
            StackTraceElement("fuera.nombre.privado", "dato", "privado.txt", 12))
        journal.record(Check.REALTIME, Fault.INITIALIZATION, error)
        val result = ErrorJournal(directory.root).report()
        assertTrue(result.contains("IllegalStateException")); assertTrue(result.contains("RealtimeClient.kt:123"))
        listOf("secreto-personal", "privado.invalid", "credencial", "otra clave", "dato-privado", "fuera.nombre", "privado.txt").forEach { assertFalse(result.contains(it)) }
    }
    @Test fun `conserva ultimo cierre tras crear otra instancia y puede borrarse`() {
        ErrorJournal(directory.root).record(Check.CONTROL, Fault.UNCAUGHT, IllegalStateException("no guardar"))
        val restored = ErrorJournal(directory.root)
        assertTrue(restored.report().contains("UNCAUGHT"))
        restored.clear()
        assertFalse(restored.report().contains("UNCAUGHT")); assertTrue(directory.root.listFiles().orEmpty().isEmpty())
    }
    @Test fun `rota y limita archivos en una sesion larga`() {
        val journal = ErrorJournal(directory.root)
        repeat(4000) { journal.record(Check.MICROPHONE, Fault.OBSERVED_ERROR) }
        assertTrue(directory.root.listFiles().orEmpty().size <= 3)
        assertTrue(directory.root.listFiles().orEmpty().all { it.length() <= ErrorJournal.MAX_BYTES })
        assertTrue(journal.report().length < ErrorJournal.MAX_BYTES * 3 + 1024)
    }
    @Test fun `caduca los registros y el informe del cierre`() {
        val journal = ErrorJournal(directory.root)
        journal.record(Check.CONTROL, Fault.UNCAUGHT)
        directory.root.listFiles().orEmpty().forEach { assertTrue(it.setLastModified(System.currentTimeMillis() - ErrorJournal.RETENTION - 10000)) }
        assertFalse(journal.report().contains("UNCAUGHT"))
    }
    @Test fun `tipos no conocidos nunca incorporan nombres externos`() {
        assertEquals("OtroFallo", ErrorJournal.safeType(object : RuntimeException("secreto") {}))
        assertEquals("LinkageError", ErrorJournal.safeType(UnsatisfiedLinkError("ruta-privada")))
        assertEquals("", ErrorJournal.frames(null))
    }
}
