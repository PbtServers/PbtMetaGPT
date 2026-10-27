package com.pbtservers.pbtmetagpt

import okhttp3.*
import java.util.concurrent.TimeUnit

/** Pool HTTP/2 compartido; sin interceptores de logs ni caché de conversaciones. */
object HostedHttp {
    private val pool = ConnectionPool(4, 2, TimeUnit.MINUTES)
    private val base = OkHttpClient.Builder().connectionPool(pool).retryOnConnectionFailure(false)
        .followRedirects(false).followSslRedirects(false).eventListenerFactory { call ->
            object : EventListener() {
                private var connectStart = 0L
                override fun connectStart(call: Call, address: java.net.InetSocketAddress, proxy: java.net.Proxy) { connectStart = System.nanoTime() }
                override fun connectEnd(call: Call, address: java.net.InetSocketAddress, proxy: java.net.Proxy, protocol: Protocol?) {
                    if (connectStart != 0L) call.request().tag(VoiceLatency::class.java)?.add(VoiceStage.TTS_CONNECT, (System.nanoTime() - connectStart) / 1000000)
                }
            }
        }.build()
    val groq = base.newBuilder().callTimeout(45, TimeUnit.SECONDS).build()
    val qwen = base.newBuilder().callTimeout(90, TimeUnit.SECONDS).readTimeout(85, TimeUnit.SECONDS).build()
    val qwenApi by lazy { QwenSpaceApi(qwen) }
}
