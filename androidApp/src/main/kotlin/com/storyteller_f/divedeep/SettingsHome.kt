package com.storyteller_f.divedeep

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun SettingsHome(
    enabled: Boolean,
    config: TranslationConfig,
    blockedCount: Int,
    onToggle: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenTranslation: () -> Unit,
    onOpenApps: () -> Unit,
) {
    BoxWithConstraints {
        val compact = maxHeight < 900.dp
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = if (compact) 20.dp else 24.dp)) {
            item { HomeHeading(compact) }
            item {
                TranslationAction(enabled, onToggle)
                Spacer(Modifier.height(if (compact) 16.dp else 24.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
            item {
                HomeSettingsRow(
                    icon = R.drawable.ic_accessibility,
                    title = "无障碍服务",
                    description = "需要开启无障碍服务以读取屏幕内容，实现跨应用翻译。",
                    actionLabel = "去授权",
                    compact = compact,
                    onClick = onOpenAccessibilitySettings,
                )
            }
            item {
                HomeSettingsRow(
                    icon = R.drawable.ic_storage,
                    title = "翻译服务",
                    subtitle = if (config.backend == TranslationBackend.LocalLlmdIpc) "本机 llmd" else "HTTP / HTTPS API",
                    description = if (config.useMockTranslation) {
                        "当前使用模拟翻译，仅用于调试。"
                    } else if (config.backend == TranslationBackend.LocalLlmdIpc) {
                        "通过本机 llmd 处理翻译，点击配置服务与授权。"
                    } else {
                        "使用兼容的 API 服务，点击管理连接与模型。"
                    },
                    onClick = onOpenTranslation,
                    compact = compact,
                )
            }
            item {
                HomeSettingsRow(
                    icon = R.drawable.ic_block,
                    title = "应用排除",
                    description = if (blockedCount == 0) {
                        "管理不需要翻译的应用，避免在特定应用中自动翻译。"
                    } else {
                        "已排除 $blockedCount 个应用，点击管理翻译范围。"
                    },
                    onClick = onOpenApps,
                    compact = compact,
                )
            }
            item { TranslationExample(compact) }
        }
    }
}

@Composable
private fun HomeHeading(compact: Boolean) {
    Text("DiveDeep", fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1).sp)
    Text("屏幕翻译", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
    Text("读懂眼前的每一句。", fontSize = 32.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(
        "跨应用实时翻译屏幕上的文字，\n让信息不再有语言的边界。",
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodyLarge,
    )
    Spacer(Modifier.height(if (compact) 20.dp else 28.dp))
}

@Composable
private fun TranslationAction(enabled: Boolean, onToggle: () -> Unit) {
    Button(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(54.dp)) {
        Icon(
            painterResource(if (enabled) R.drawable.ic_stop_circle else R.drawable.ic_play_arrow),
            contentDescription = null,
        )
        Text(
            if (enabled) "停止翻译" else "开始翻译",
            modifier = Modifier.padding(start = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
    Text(
        if (enabled) "翻译开关已开启 · 请确认无障碍服务已授权" else "翻译已关闭 · 也可通过快捷设置开启",
        Modifier.fillMaxWidth().padding(top = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
private fun HomeSettingsRow(
    @DrawableRes icon: Int,
    title: String,
    description: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    actionLabel: String? = null,
    compact: Boolean = false,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
                .padding(vertical = if (compact) 12.dp else 20.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(40.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    actionLabel?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Icon(
                        painterResource(R.drawable.ic_chevron_right),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                subtitle?.let { Text(it, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 3.dp)) }
                Text(
                    description,
                    Modifier.padding(top = 6.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun TranslationExample(compact: Boolean) {
    Text(
        "效果示意",
        Modifier.padding(top = if (compact) 12.dp else 16.dp, bottom = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
            .padding(if (compact) 12.dp else 18.dp),
    ) {
        Text(
            "原文",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            lineHeight = if (compact) 14.sp else 16.sp,
        )
        Text(
            "Read beyond the words.",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 4.dp),
            lineHeight = if (compact) 20.sp else 24.sp,
        )
        Icon(
            painterResource(R.drawable.ic_arrow_downward),
            contentDescription = null,
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = if (compact) 2.dp else 8.dp)
                .size(if (compact) 16.dp else 18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "译文",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium,
            lineHeight = if (compact) 14.sp else 16.sp,
        )
        Text(
            "读懂文字背后的世界。",
            Modifier.padding(top = 4.dp),
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            lineHeight = if (compact) 20.sp else 24.sp,
        )
    }
}
