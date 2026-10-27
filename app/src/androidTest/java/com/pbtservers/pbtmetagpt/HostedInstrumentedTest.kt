package com.pbtservers.pbtmetagpt

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Keystore real del emulador. No acredita conexión ni generación remota. */
@RunWith(AndroidJUnit4::class)
class HostedInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun credencialesGroqYOpenAIPermanecenSeparadasYNoSeExportan() {
        val groq = KeyVault(context, provider = "groq")
        val openai = KeyVault(context)
        val freeKey = "gsk_" + "a".repeat(40)
        val optionalKey = "sk-" + "b".repeat(40)
        try {
            groq.save(freeKey); openai.save(optionalKey)
            assertEquals(freeKey, KeyVault(context, provider = "groq").read())
            assertEquals(optionalKey, openai.read())
            val values = context.getSharedPreferences("credenciales_groq_cifradas", 0).all.values
            assertFalse(values.any { it.toString().contains(freeKey) })
            val exported = AssistantProfile.export(listOf(AssistantProfile(name = "Luna")))
            assertFalse(exported.contains(freeKey)); assertFalse(exported.contains(optionalKey))
            groq.delete(); assertEquals(optionalKey, openai.read())
        } finally { groq.delete(); openai.delete() }
    }
    @Test fun consentimientoEsIndependientePorServicioYNoActivaFacturacion() {
        val prefs = context.getSharedPreferences("servicios_alojados", 0)
        val settings = HostedSettings(context)
        try {
            prefs.edit().clear().commit()
            settings.select("groq"); settings.saveGroq("openai/gpt-oss-20b", true)
            assertTrue(settings.authorized); assertFalse(settings.qwenAuthorized)
            settings.authorizeQwen(true); settings.saveGroq(settings.model, false)
            assertTrue(settings.qwenAuthorized); assertFalse(settings.authorized)
            assertEquals("groq", settings.provider)
        } finally { prefs.edit().clear().commit() }
    }
}
