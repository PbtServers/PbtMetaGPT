package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in real: dos peticiones sintéticas, sin token, grabaciones ni reproducción simulada. */
class QwenLivePerformanceTest {
    @Test fun comparaAudioCompletoConPrimerFragmentoReal(): Unit = runBlocking {
        assumeTrue("Solo se ejecuta contra Internet por elección explícita", System.getenv("PBT_QWEN_LIVE") == "1")
        val first = "Hola, soy un asistente de voz. Esta muestra permite comprobar el primer fragmento de audio."
        val whole = "$first Después de esta primera frase, continúo con una explicación breve para comparar la espera antes de disponer de audio."
        val profile = AssistantProfile(name = "Prueba", ttsEngine = "qwen_space", ttsVoice = "Ryan", ttsPackage = "0.6B", voiceMode = "natural")
        val report = JSONObject().put("tipo", "INFERENCIA_REAL_SIN_MOCKS").put("limite", "PCM disponible no equivale a sonido físico. Dos peticiones, sin demostrar superioridad general; GPU/cachés y cola pueden variar.")
        val output = File("build/reports/qwen-latencia-real.json").apply { parentFile.mkdirs() }
        var stage = "COMPLETO"
        try {
            val baseline = QwenSpaceApi(OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).callTimeout(90, TimeUnit.SECONDS).build())
            val before = VoiceLatency()
            val old = baseline.synthesize(profile, whole, {}, before)
            before.mark(VoiceStage.TOTAL)
            assertTrue(old.bytes.any { it.toInt() != 0 })
            report.put("completo_ms", JSONObject(before.snapshot().mapKeys { it.key.name }))
            stage = "PRIMER_FRAGMENTO"
            val after = VoiceLatency()
            val early = HostedHttp.qwenApi.synthesize(profile, first, {}, after)
            after.mark(VoiceStage.TOTAL)
            assertTrue(early.bytes.any { it.toInt() != 0 })
            report.put("primer_fragmento_ms", JSONObject(after.snapshot().mapKeys { it.key.name })).put("resultado", "CORRECTO_REAL")
            val oldMs = before.snapshot().getValue(VoiceStage.TOTAL)
            val firstMs = after.snapshot().getValue(VoiceStage.TOTAL)
            System.getenv("GITHUB_OUTPUT")?.let { File(it).appendText("completo_ms=$oldMs\nfragmento_ms=$firstMs\n") }
        } catch (problem: Exception) {
            report.put("resultado", "NO_COMPLETADO").put("tipo_error", problem.javaClass.simpleName).put("etapa", stage)
                .put("mensaje_publico", (problem as? HostedFailure)?.publicMessage ?: "No se completó la inferencia real.")
            throw AssertionError("No se completó la comparación remota; consulta el informe sin contenido.")
        } finally { output.writeText(report.toString(2)) }
        Unit
    }
}
