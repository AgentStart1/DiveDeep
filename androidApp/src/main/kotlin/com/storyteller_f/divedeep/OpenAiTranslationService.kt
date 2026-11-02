package com.storyteller_f.divedeep

import com.storyteller_f.divedeep.shared.MockTranslationService
import com.storyteller_f.divedeep.shared.TranslationItem
import com.storyteller_f.divedeep.shared.TranslationRequest
import com.storyteller_f.divedeep.shared.TranslationService
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAiTranslationService(
    private val configProvider: () -> TranslationConfig,
) : TranslationService {
    private val mockTranslationService = MockTranslationService()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    override suspend fun translate(request: TranslationRequest): List<TranslationItem> {
        if (request.items.isEmpty()) return emptyList()

        val config = configProvider()
        if (config.useMockTranslation) {
            return mockTranslationService.translate(request)
        }

        val content = OpenAiTranslationProtocol.extractContent(requestTranslations(config, request))
        val translatedTexts = OpenAiTranslationProtocol.parseTranslations(content, request.items.size)
        return OpenAiTranslationProtocol.toTranslationItems(request, translatedTexts)
    }

    private suspend fun requestTranslations(config: TranslationConfig, request: TranslationRequest): String {
        val payload = OpenAiTranslationProtocol.buildChatCompletionPayload(config, request).toString()
        val httpRequest = Request.Builder()
            .url(chatCompletionsUrl(config.apiBaseUrl))
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .apply {
                config.apiKey.takeIf { it.isNotBlank() }?.let { header("Authorization", "Bearer $it") }
            }
            .build()
        return client.newCall(httpRequest).awaitTranslationResponse()
    }

    fun close() {
        client.dispatcher.cancelAll()
        client.connectionPool.evictAll()
        client.dispatcher.executorService.shutdown()
    }

    private fun chatCompletionsUrl(apiBaseUrl: String): String =
        "${apiBaseUrl.trimEnd('/')}/v1/chat/completions"
}

object OpenAiTranslationProtocol {
    fun buildChatCompletionPayload(config: TranslationConfig, request: TranslationRequest): JSONObject =
        JSONObject()
            .put("model", config.model)
            .put("stream", false)
            .put(
                "messages",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("role", "system")
                            .put(
                                "content",
                                "Translate UI text. Return only a JSON array of strings, " +
                                    "one translated string per input item, preserving order.",
                            ),
                    )
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", userPrompt(request)),
                    ),
            )

    fun extractContent(responseBody: String): String {
        val response = JSONObject(responseBody)
        response.optJSONObject("error")?.let { error ->
            throw TranslationException(
                "Translation API error: ${error.optString("message", error.toString())}",
            )
        }
        return response
            .getJSONArray("choices")
            .getJSONObject(0)
            .getJSONObject("message")
            .getString("content")
    }

    fun parseTranslations(content: String, expectedCount: Int): List<String> {
        val normalized = stripCodeFence(content.trim())
        runCatching {
            val array = JSONArray(normalized)
            return List(array.length()) { index -> array.getString(index) }
                .also { requireExpectedCount(it, expectedCount) }
        }

        return normalized
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { it.replace(Regex("""^\s*(?:[-*]|\d+[.)])\s*"""), "") }
            .toList()
            .also { requireExpectedCount(it, expectedCount) }
    }

    fun toTranslationItems(
        request: TranslationRequest,
        translatedTexts: List<String>,
    ): List<TranslationItem> =
        request.items.mapIndexed { index, node ->
            TranslationItem(
                nodeId = node.id,
                sourceText = node.text,
                translatedText = translatedTexts[index],
                bounds = node.bounds,
            )
        }

    private fun userPrompt(request: TranslationRequest): String {
        val items = JSONArray()
        request.items.forEachIndexed { index, node ->
            items.put(
                JSONObject()
                    .put("index", index)
                    .put("text", node.text),
            )
        }
        return "Target language: ${request.targetLanguage}\nInput JSON:\n$items"
    }

    private fun stripCodeFence(content: String): String {
        if (!content.startsWith("```")) return content
        return content.lines()
            .drop(1)
            .dropLastWhile { it.trim() == "```" }
            .joinToString("\n")
            .trim()
    }

    private fun requireExpectedCount(translatedTexts: List<String>, expectedCount: Int) {
        if (translatedTexts.size != expectedCount) {
            throw TranslationException(
                "Translation response item count ${translatedTexts.size} did not match request item count $expectedCount",
            )
        }
    }
}

class TranslationException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
