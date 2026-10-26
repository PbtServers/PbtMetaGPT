package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class RealtimeHttpTest {
    @Test fun `una respuesta lenta no bloquea otras corrutinas y se consume completa`() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("respuesta completa").setBodyDelay(300, TimeUnit.MILLISECONDS))
            val reply = async { client.newCall(Request.Builder().url(server.url("/")).build()).awaitReply() }
            var ticks = 0
            repeat(5) { delay(10); ticks++ }
            assertEquals(5, ticks)
            assertEquals("respuesta completa", reply.await().body)
            server.enqueue(MockResponse().setBody("segunda respuesta"))
            assertTrue(client.newCall(Request.Builder().url(server.url("/")).build()).awaitReply().isSuccessful)
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.shutdown() }
    }
    @Test fun `cancelar mientras llega el cuerpo cancela tambien la llamada HTTP`() = runBlocking {
        val server = MockWebServer()
        val client = OkHttpClient()
        server.start()
        try {
            server.enqueue(MockResponse().setBody("respuesta lenta").setBodyDelay(1, TimeUnit.SECONDS))
            val call = client.newCall(Request.Builder().url(server.url("/")).build())
            val pending = launch { call.awaitReply() }
            withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
            pending.cancelAndJoin()
            assertTrue(call.isCanceled())
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown(); server.shutdown() }
    }
}
