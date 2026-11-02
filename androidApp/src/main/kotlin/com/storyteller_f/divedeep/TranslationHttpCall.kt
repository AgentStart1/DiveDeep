package com.storyteller_f.divedeep

import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

/** Cancellation aborts the socket, including while the callback is reading the response body. */
internal suspend fun Call.awaitTranslationResponse(): String = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWithException(e)
        }

        override fun onResponse(call: Call, response: Response) {
            // Consume and close on OkHttp's I/O thread before handing back an immutable value.
            // Returning Response here would detach body reads from the cancellation handler.
            val body = try {
                response.use { it.body.string() }
            } catch (error: IOException) {
                continuation.resumeWithException(error)
                return
            }
            if (response.isSuccessful) {
                continuation.resume(body)
            } else {
                continuation.resumeWithException(
                    TranslationException("Translation API failed with HTTP ${response.code}: $body"),
                )
            }
        }
    })
}
