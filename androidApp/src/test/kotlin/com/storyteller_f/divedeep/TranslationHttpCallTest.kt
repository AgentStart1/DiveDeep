package com.storyteller_f.divedeep

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TranslationHttpCallTest {
    @Test
    fun cancellingBeforeHeadersAbortsTheCallAndFreesTheNextRequest() = runBlocking {
        // A real socket needs a wall-clock coroutine scope rather than virtual test time.
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody("next response"))
            val failed = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) { failed.complete(Unit) }
            }).build()
            client.dispatcher.maxRequestsPerHost = 1
            try {
                withTimeout(10_000) {
                    val oldCall = client.newCall(Request.Builder().url(server.url("/old")).build())
                    val old = launch { oldCall.awaitTranslationResponse() }
                    assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(5, TimeUnit.SECONDS) })

                    old.cancelAndJoin()
                    failed.await()
                    val next = client.newCall(Request.Builder().url(server.url("/next")).build())

                    assertEquals("next response", next.awaitTranslationResponse())
                    assertTrue(oldCall.isCanceled())
                    assertTrue(old.isCancelled)
                }
            } finally {
                close(client)
            }
        }
    }

    @Test
    fun cancellationWhileReadingBodyAbortsTheSocket() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("unfinished response").throttleBody(1, 100, TimeUnit.MILLISECONDS))
            val reading = CompletableDeferred<Unit>()
            val failed = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().eventListener(object : EventListener() {
                override fun responseBodyStart(call: Call) { reading.complete(Unit) }
                override fun callFailed(call: Call, ioe: IOException) { failed.complete(Unit) }
            }).build()
            try {
                withTimeout(10_000) {
                    val call = client.newCall(Request.Builder().url(server.url("/body")).build())
                    val job = launch { call.awaitTranslationResponse() }
                    reading.await()

                    job.cancelAndJoin()
                    failed.await()

                    assertTrue(call.isCanceled())
                    assertTrue(job.isCancelled)
                }
            } finally {
                close(client)
            }
        }
    }

    private fun close(client: OkHttpClient) {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }
}
