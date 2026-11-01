package com.storyteller_f.divedeep

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** In-memory form state; credentials are never written into saved instance state. */
@Stable
internal class TranslationSettingsDraft(config: TranslationConfig) {
    var backend by mutableStateOf(config.backend)
    var llmdTarget by mutableStateOf(config.llmdTarget)
    var apiBaseUrl by mutableStateOf(config.apiBaseUrl)
    var model by mutableStateOf(config.model)
    val apiKey = TextFieldState(config.apiKey)
    var useMockTranslation by mutableStateOf(config.useMockTranslation)

    fun toConfig() = TranslationConfig(
        backend = backend,
        llmdTarget = llmdTarget,
        apiBaseUrl = apiBaseUrl.trim(),
        model = model.trim(),
        apiKey = apiKey.text.toString().trim(),
        useMockTranslation = useMockTranslation,
    )
}

