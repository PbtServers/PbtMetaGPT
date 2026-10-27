package com.pbtservers.pbtmetagpt

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class StorageInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun guardarSustituirEliminarConKeystoreRealDelEmulador() {
        val vault = KeyVault(context)
        val a = "sk-" + "a".repeat(40)
        val b = "sk-" + "b".repeat(40)
        try {
            vault.save(a); assertTrue(vault.exists()); assertEquals(a, KeyVault(context).read())
            val prefs = context.getSharedPreferences("credenciales_cifradas", Context.MODE_PRIVATE)
            assertFalse(prefs.all.values.any { it.toString().contains(a) })
            val previous = prefs.getString("ciphertext", null)
            vault.save(b); assertEquals(b, vault.read()); assertNotEquals(previous, prefs.getString("ciphertext", null))
            assertTrue(runCatching { vault.save("invalida") }.isFailure)
            assertEquals(b, vault.read())
            prefs.edit().putString("ciphertext", "alterado").commit()
            assertTrue(runCatching { vault.read() }.isFailure)
        } finally { vault.delete() }
        assertFalse(vault.exists()); assertTrue(runCatching { vault.read() }.isFailure)
        assertFalse(KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.containsAlias("PbtMetaGPT.OpenAI.v1"))
    }
    @Test fun perfilesPersistenYElCambioDeNombreModificaLaFrase() {
        val store = ProfileStore(context)
        val jarvis = AssistantProfile(name = "Jarvis", voice = "cedar")
        try {
            store.save(listOf(jarvis), jarvis.id)
            assertEquals(jarvis, ProfileStore(context).load().single())
            assertEquals(jarvis.id, ProfileStore(context).selected())
            store.save(listOf(jarvis.copy(name = "Alex")), jarvis.id)
            assertEquals("Atento Alex", ProfileStore(context).load().single().phrase)
            assertTrue(ProfileStore(context).load().none { it.phrase == "Atento Jarvis" })
        } finally { store.save(emptyList(), null) }
        assertTrue(store.load().isEmpty())
    }
}
