package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class VoiceApiHttpTest {
    @Test fun `API PCM valida formato y rechaza error o JSON sin propagar cuerpo`() = runBlocking {
        val server = MockWebServer(); val http = OkHttpClient(); server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/pcm").setBody(Buffer().write(byteArrayOf(1, 0, 2, 0))))
            assertArrayEquals(byteArrayOf(1, 0, 2, 0), http.newCall(Request.Builder().url(server.url("/")).build()).awaitPcm())
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("{}"))
            assertTrue(runCatching { http.newCall(Request.Builder().url(server.url("/")).build()).awaitPcm() }.isFailure)
            val secret = "sk-" + "f".repeat(40)
            server.enqueue(MockResponse().setResponseCode(401).setBody(secret))
            val failure = runCatching { http.newCall(Request.Builder().url(server.url("/")).build()).awaitPcm() }.exceptionOrNull()
            assertNotNull(failure); assertFalse(failure!!.message.orEmpty().contains(secret))
        } finally { server.shutdown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
    @Test fun `cancelar sintesis cancela HTTP y no entrega audio pendiente`() = runBlocking {
        val server = MockWebServer(); val http = OkHttpClient(); server.start()
        try {
            server.enqueue(MockResponse().setHeader("Content-Type", "audio/pcm").setBody(Buffer().write(byteArrayOf(1, 0))).setBodyDelay(1, TimeUnit.SECONDS))
            val call = http.newCall(Request.Builder().url(server.url("/")).build())
            val pending = launch { call.awaitPcm(); fail("Audio entregado tras cancelar") }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            pending.cancelAndJoin(); assertTrue(call.isCanceled())
        } finally { server.shutdown(); http.connectionPool.evictAll(); http.dispatcher.executorService.shutdown() }
    }
}
