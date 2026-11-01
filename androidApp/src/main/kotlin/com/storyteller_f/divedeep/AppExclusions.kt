package com.storyteller_f.divedeep

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
internal fun AppExclusions(
    apps: List<AppEntry>,
    blockedPackages: Set<String>,
    onPackageBlockedChange: (String, Boolean) -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
        item {
            Text("选择无需翻译的应用", style = MaterialTheme.typography.headlineSmall)
            Text(
                "勾选后，此应用将不会显示翻译。修改会自动保存。",
                Modifier.padding(top = 8.dp, bottom = 16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (apps.isEmpty()) {
            item {
                Text(
                    "暂无可管理的应用",
                    Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(apps, key = { it.packageName }) { app ->
            AppFilterRow(app, app.packageName in blockedPackages) { blocked ->
                onPackageBlockedChange(app.packageName, blocked)
            }
        }
    }
}

@Composable
private fun AppFilterRow(app: AppEntry, blocked: Boolean, onBlockedChange: (Boolean) -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().toggleable(value = blocked, role = Role.Checkbox, onValueChange = onBlockedChange)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.label, style = MaterialTheme.typography.bodyLarge)
                Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Checkbox(checked = blocked, onCheckedChange = null)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
