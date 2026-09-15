package dev.mediasearch.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.mediasearch.BuildConfig

@Composable
fun SettingsScreen(onOpenAbout: () -> Unit, modifier: Modifier = Modifier) {
    val preferences = LocalThemePreferences.current
    val dynamicSupported = android.os.Build.VERSION.SDK_INT >= 31

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("外观", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "选择适合你的主题色，修改会立即应用并保存在本机。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = dynamicSupported) { preferences.useSystemColors(!preferences.useDynamicColors) }
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("系统动态颜色", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (dynamicSupported) "跟随系统壁纸生成协调的浅色与深色主题"
                                else "Android 12 及以上可用；当前设备会使用预设主题色",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = preferences.useDynamicColors,
                            onCheckedChange = preferences::useSystemColors,
                            enabled = dynamicSupported
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text(
                        "预设主题色",
                        modifier = Modifier.padding(start = 18.dp, top = 16.dp, end = 18.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                    ThemePalette.entries.forEach { palette ->
                        ThemePaletteOption(
                            palette = palette,
                            selected = (!preferences.useDynamicColors || !dynamicSupported) && preferences.palette == palette,
                            onSelected = { preferences.choosePalette(palette) }
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
        item {
            Text("关于", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            ListItem(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onOpenAbout),
                headlineContent = { Text("关于 OpenScope") },
                supportingContent = { Text("版本、来源与许可") },
                trailingContent = { Icon(AppIcons.ChevronRight, contentDescription = null) }
            )
        }
    }
}

@Composable
private fun ThemePaletteOption(
    palette: ThemePalette,
    selected: Boolean,
    onSelected: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                role = Role.RadioButton
                contentDescription = "主题色 ${palette.label}"
            }
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelected)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(palette.lightPrimary)
        )
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(palette.label, style = MaterialTheme.typography.bodyLarge)
            Text(palette.description, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
fun AboutScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var showLicenses by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val license = remember {
        runCatching {
            context.assets.open("licenses/MediaCrawler-LICENSE.txt").bufferedReader().use { it.readText() }
        }.getOrDefault("许可文件暂不可用")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("关于") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(AppIcons.ArrowBack, contentDescription = "返回设置")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item { Text("OpenScope", style = MaterialTheme.typography.displayMedium) }
            item { Text("让好奇心走得更远。", style = MaterialTheme.typography.titleLarge) }
            item {
                Text(
                    "Android 原生验证版 · ${BuildConfig.VERSION_NAME}\n\n哔哩哔哩关键词搜索已取到真实结果，但可能触发平台验证；知乎登录与搜索闭环待验证；小红书通过官方网页读取搜索卡片，真实账号闭环仍待验证。\n\n登录信息保留在 App 本地。没有云端代抓服务。",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            item { Text("学习与研究用途", style = MaterialTheme.typography.titleMedium) }
            item {
                Text(
                    "部分平台适配参考 MediaCrawler，遵循 NON-COMMERCIAL LEARNING LICENSE 1.1。版权所有 © 2024 relakkes@gmail.com。",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            item { OutlinedLicenseButton(onClick = { showLicenses = true }) }
        }
    }

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text("来源与许可") },
            text = {
                LazyColumn(Modifier.height(420.dp)) {
                    item {
                        Text(
                            "MediaCrawler @ 8773e47\nhttps://github.com/KellenGO/MediaCrawler\n\n$license",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } }
        )
    }
}

@Composable
private fun OutlinedLicenseButton(onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(onClick = onClick) { Text("来源与许可") }
}
