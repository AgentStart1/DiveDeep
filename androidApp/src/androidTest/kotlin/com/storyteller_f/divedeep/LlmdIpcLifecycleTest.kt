package com.storyteller_f.divedeep

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import androidx.test.platform.app.InstrumentationRegistry
import com.storytellerf.llmd.ipc.ILlmdChatCallback
import com.storytellerf.llmd.ipc.ILlmdService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Model-free connection ownership checks; real cross-app IPC remains in OverlayLlmdIntegrationTest. */
class LlmdIpcLifecycleTest {
    @Test
    fun disconnectionFailsEveryInFlightRequestPromptly() = assertPendingCallsFail { context, _ ->
        context.connection.onServiceDisconnected(context.component)
    }

    @Test
    fun bindingDeathFailsEveryInFlightRequestPromptly() = assertPendingCallsFail { context, _ ->
        context.connection.onBindingDied(context.component)
    }

    @Test
    fun closingClientFailsPendingCallsAndLateCallbacksCannotReviveThem() = assertPendingCallsFail { _, client ->
        client.close()
    }

    @Test
    fun cancelledCallsCannotCompleteTheNextRequest() = runBlocking {
        val context = TestBindingContext()
        val client = LlmdIpcTranslationService(context) { TranslationConfig(llmdTarget = LlmdTarget.Debug) }
        try {
            withTimeout(TEST_TIMEOUT) {
                val first = async(Dispatchers.IO) { client.health() }
                val oldCallback = context.callbacks.receive()
                first.cancelAndJoin()
                val second = async(Dispatchers.IO) { client.health() }
                val newCallback = context.callbacks.receive()
                oldCallback.onComplete("old response")
                newCallback.onComplete("current response")
                assertEquals("current response", second.await())
                assertTrue(first.isCancelled)
            }
        } finally {
            client.close()
        }
    }

    // JUnit4 is the synchronous boundary; timeout proves we do not wait for the 120-second IPC deadline.
    private fun assertPendingCallsFail(disconnect: (TestBindingContext, LlmdIpcTranslationService) -> Unit) = runBlocking {
        val context = TestBindingContext()
        val client = LlmdIpcTranslationService(context) { TranslationConfig(llmdTarget = LlmdTarget.Debug) }
        try {
            withTimeout(TEST_TIMEOUT) {
                val first = async(Dispatchers.IO) { awaitHealthFailure(client) }
                val second = async(Dispatchers.IO) { awaitHealthFailure(client) }
                val callbacks = listOf(context.callbacks.receive(), context.callbacks.receive())
                disconnect(context, client)
                assertTrue(first.await().message.orEmpty().contains("Local llmd IPC"))
                assertTrue(second.await().message.orEmpty().contains("Local llmd IPC"))
                callbacks.forEach { it.onComplete("late response") }
            }
        } finally {
            client.close()
        }
    }

    private suspend fun awaitHealthFailure(client: LlmdIpcTranslationService): TranslationException = try {
        client.health()
        error("A disconnected request unexpectedly succeeded")
    } catch (failure: TranslationException) {
        failure
    }

    private class TestBindingContext : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        val callbacks = Channel<ILlmdChatCallback>(Channel.UNLIMITED)
        lateinit var connection: ServiceConnection
        lateinit var component: ComponentName
        private val remote = object : ILlmdService.Stub() {
            override fun healthAsync(callback: ILlmdChatCallback) { callbacks.trySend(callback) }
            override fun listModelsAsync(callback: ILlmdChatCallback) = error("Unexpected model-list request")
            override fun chatCompletionAsync(requestJson: String, callback: ILlmdChatCallback) = error("Unexpected chat request")
        }

        override fun getApplicationContext(): Context = this

        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
            component = checkNotNull(service.component)
            connection = conn
            conn.onServiceConnected(component, remote.asBinder())
            return true
        }

        override fun unbindService(conn: ServiceConnection) = Unit
    }

    private companion object {
        const val TEST_TIMEOUT = 5_000L
    }
}
