package com.storyteller_f.divedeep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextObfuscationMode
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedSecureTextField
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun TranslationSettings(
    draft: TranslationSettingsDraft,
    authorizationMessage: String,
    onSaveConfig: (TranslationConfig) -> Unit,
    onAuthorizeLlmd: (LlmdTarget) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text("选择翻译方式", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            SupportingText("配置完成后保存，返回首页即可开启翻译。")
        }
        item {
            Column {
                BackendOption(
                    selected = draft.backend == TranslationBackend.LocalLlmdIpc,
                    title = "本机 llmd IPC",
                    description = "通过本机 llmd 服务翻译",
                    onClick = { draft.backend = TranslationBackend.LocalLlmdIpc },
                )
                BackendOption(
                    selected = draft.backend == TranslationBackend.OpenAiHttp,
                    title = "HTTP / HTTPS API",
                    description = "连接兼容的翻译接口",
                    onClick = { draft.backend = TranslationBackend.OpenAiHttp },
                )
            }
        }
        if (draft.backend == TranslationBackend.LocalLlmdIpc) {
            item {
                LlmdSettings(draft, authorizationMessage) { onAuthorizeLlmd(draft.llmdTarget) }
            }
        }
        item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant) }
        item {
            Text("连接配置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (draft.backend == TranslationBackend.LocalLlmdIpc) {
                SupportingText("本机服务无需填写 API 地址和密钥，切换服务不会清除已有内容。")
            }
        }
        item {
            TranslationTextField(
                value = draft.apiBaseUrl,
                onValueChange = { draft.apiBaseUrl = it },
                label = "API Base URL",
                enabled = draft.backend == TranslationBackend.OpenAiHttp,
            )
        }
        item {
            TranslationTextField(value = draft.model, onValueChange = { draft.model = it }, label = "Model")
        }
        item {
            OutlinedSecureTextField(
                state = draft.apiKey,
                enabled = draft.backend == TranslationBackend.OpenAiHttp,
                label = { Text("API Key") },
                textObfuscationMode = TextObfuscationMode.Hidden,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            SaveConfigButton(draft, authorizationMessage, onSaveConfig)
        }
        item { MockTranslationToggle(draft) }
    }
}

@Composable
private fun SaveConfigButton(
    draft: TranslationSettingsDraft,
    authorizationMessage: String,
    onSaveConfig: (TranslationConfig) -> Unit,
) {
    Column {
        Button(onClick = { onSaveConfig(draft.toConfig()) }, modifier = Modifier.fillMaxWidth()) {
            Text("保存翻译配置", modifier = Modifier.padding(vertical = 6.dp))
        }
        if (draft.backend == TranslationBackend.OpenAiHttp && authorizationMessage.isNotBlank()) {
            Text(
                authorizationMessage,
                Modifier.padding(top = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun MockTranslationToggle(draft: TranslationSettingsDraft) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Text(
            "开发调试",
            Modifier.padding(top = 16.dp),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            Modifier.fillMaxWidth().toggleable(
                value = draft.useMockTranslation,
                role = Role.Checkbox,
                onValueChange = { draft.useMockTranslation = it },
            ).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = draft.useMockTranslation, onCheckedChange = null)
            Column(Modifier.weight(1f)) {
                Text("使用 Mock 翻译", style = MaterialTheme.typography.bodyMedium)
                SupportingText("仅用于调试，不会生成真实译文；修改后需保存。")
            }
        }
    }
}

@Composable
private fun LlmdSettings(draft: TranslationSettingsDraft, authorizationMessage: String, onAuthorize: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("llmd 版本", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LlmdTarget.entries.forEach { target ->
            BackendOption(
                selected = draft.llmdTarget == target,
                title = target.displayName,
                onClick = { draft.llmdTarget = target },
            )
        }
        OutlinedButton(onClick = onAuthorize, modifier = Modifier.fillMaxWidth()) {
            Text("授权 llmd ${draft.llmdTarget.displayName}")
        }
        if (authorizationMessage.isNotBlank()) {
            Text(
                authorizationMessage,
                Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun BackendOption(selected: Boolean, title: String, onClick: () -> Unit, description: String? = null) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            description?.let { SupportingText(it) }
        }
    }
}

@Composable
private fun TranslationTextField(value: String, onValueChange: (String) -> Unit, label: String, enabled: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SupportingText(text: String) {
    Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
}
