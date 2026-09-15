package dev.mediasearch.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediasearch.*
import dev.mediasearch.core.*
import dev.mediasearch.session.SessionStatus
import kotlinx.coroutines.launch
import java.net.URLEncoder

private data class BrowserDestination(val platform: Platform, val url: String, val login: Boolean)

/** How many results of one platform are shown before the user asks for more. */
private const val INITIAL_VISIBLE_RESULTS = 3

/** How many extra results one tap on "更多" reveals (already fetched ones first). */
private const val VISIBLE_RESULTS_STEP = 3

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaSearchApp(model: SearchViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    val sessions by model.sessions.statuses.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var browser by remember { mutableStateOf<BrowserDestination?>(null) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    // Hoisted above the browser/about branches: these composables are removed from the
    // composition while a detail page is open, so list state remembered inside them would be
    // thrown away. One state per list keeps the two scroll positions independent.
    val searchListState = rememberLazyListState()
    val accountListState = rememberLazyListState()

    // Per-platform "how many results are visible" counters, keyed on the search round.
    // SearchViewModel bumps state.searchId on every search() — including a repeat of the same
    // query — so a new search starts at three again, while retry/more and returning from a
    // detail page keep the counts. Like the list states above, this lives outside the branches.
    val visibleCounts = remember(state.searchId) { mutableStateMapOf<Platform, Int>() }

    fun login(platform: Platform) { browser = BrowserDestination(platform, platform.homeUrl, true) }
    fun webSearch(platform: Platform) {
        val query = URLEncoder.encode(state.query.ifBlank { state.input }.trim(), "UTF-8")
        val url = when (platform) {
            Platform.XHS -> "https://www.xiaohongshu.com/search_result?keyword=$query&source=web_explore_feed"
            Platform.ZHIHU -> "https://www.zhihu.com/search?type=content&q=$query"
            Platform.BILIBILI -> "https://search.bilibili.com/all?keyword=$query"
        }
        browser = BrowserDestination(platform, url, false)
    }
    fun startSearch() {
        model.search()
        // A new search starts at the top. Opening a detail page and coming back takes neither
        // this path nor a state.searchId change, so it keeps the scroll position and the counts.
        scope.launch { searchListState.scrollToItem(0) }
    }
    CollectionTheme {
        val destination = browser
        if (destination != null) {
            PlatformBrowser(destination.platform, destination.url, destination.login, model,
                onClose = { browser = null },
                onVerified = { browser = null; model.retry(destination.platform) })
        } else if (tab == 2 && showAbout) {
            AboutScreen(onBack = { showAbout = false })
        } else {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = { Text(if (tab == 2) "设置" else "OpenScope", fontWeight = FontWeight.Bold, letterSpacing = 3.sp) },
                        navigationIcon = {
                            Icon(
                                AppIcons.Explore,
                                contentDescription = "OpenScope",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 24.dp).size(28.dp)
                            )
                        },
                        actions = { TextButton(onClick = { tab = 1 }) { Text("账号") } },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                },
                bottomBar = {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                        listOf(
                            "搜索" to AppIcons.Explore,
                            "账号" to AppIcons.Person,
                            "设置" to AppIcons.Settings
                        ).forEachIndexed { index, (label, icon) ->
                            NavigationBarItem(selected = tab == index, onClick = { tab = index },
                                icon = { Icon(icon, contentDescription = label) }, label = { Text(label) })
                        }
                    }
                }
            ) { padding ->
                when (tab) {
                    0 -> LazyColumn(
                        state = searchListState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            OutlinedTextField(
                                value = state.input, onValueChange = model::input,
                                modifier = Modifier.fillMaxWidth(), singleLine = true,
                                placeholder = { Text("搜索你感兴趣的事") },
                                shape = RoundedCornerShape(28.dp),
                                leadingIcon = { Icon(AppIcons.Search, contentDescription = "搜索", tint = MaterialTheme.colorScheme.primary) },
                                trailingIcon = {
                                    FilledTonalButton(onClick = { keyboard?.hide(); startSearch() }, enabled = state.input.isNotBlank(),
                                        contentPadding = PaddingValues(horizontal = 16.dp), modifier = Modifier.padding(end = 8.dp)) { Text("搜索") }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide(); startSearch() })
                            )
                        }
                        item {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(selected = state.selected == null, onClick = { model.select(null) }, label = { Text("全部") })
                                Platform.entries.forEach { platform ->
                                    FilterChip(selected = state.selected == platform, onClick = { model.select(platform) }, label = { Text(platform.label) })
                                }
                            }
                        }
                        if (state.query.isBlank()) {
                            item {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(24.dp)) {
                                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text("你的内容来源", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                        Platform.entries.forEach { platform ->
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                PlatformMark(platform)
                                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                                    Text(platform.label, style = MaterialTheme.typography.bodyLarge)
                                                    Text(sessions[platform]?.label.orEmpty(),
                                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                                TextButton(onClick = { login(platform) }) { Text(accountActionLabel(sessions[platform])) }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            item {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("“${state.query}”", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 2)
                                    Text("按来源呈现", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            state.results.forEach { (platform, result) ->
                                val visible = visibleCounts[platform] ?: INITIAL_VISIBLE_RESULTS
                                val shown = result.items.take(visible)
                                val hidden = result.items.size - shown.size
                                item(key = "header-${platform.name}") {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PlatformMark(platform)
                                        Text(platform.label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 10.dp))
                                        result.elapsedMs?.let { Text(if (result.cached) "缓存 · ${it}ms" else "${it}ms", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    }
                                }
                                if (result.loading) item(key = "loading-${platform.name}") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                                result.message?.let { message ->
                                    item(key = "message-${platform.name}") {
                                        StatusCard(message,
                                            when (result.failure) {
                                                FailureKind.LOGIN_REQUIRED -> "在 App 内登录"
                                                FailureKind.UNSUPPORTED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED -> "查看官方网页"
                                                else -> "重试"
                                            },
                                            onAction = { when (result.failure) {
                                                FailureKind.LOGIN_REQUIRED -> login(platform)
                                                FailureKind.UNSUPPORTED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED -> webSearch(platform)
                                                else -> model.retry(platform)
                                            } },
                                            alternate = if (result.failure !in setOf(FailureKind.UNSUPPORTED, FailureKind.LOGIN_REQUIRED, FailureKind.CHALLENGE, FailureKind.RATE_LIMITED)) ({ webSearch(platform) }) else null)
                                    }
                                }
                                if (!result.loading && result.message == null && result.items.isEmpty()) item(key = "empty-${platform.name}") { Text("没有找到相关内容，试试换个关键词。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                items(shown, key = { "${platform.name}-${it.id}" }) { entry ->
                                    ResultCard(entry) { browser = BrowserDestination(platform, entry.url, false) }
                                }
                                if (shown.isNotEmpty() && (hidden > 0 || result.hasMore)) {
                                    item(key = "more-${platform.name}") {
                                        OutlinedButton(
                                            onClick = {
                                                // Reveal the results that are already fetched first; only ask for
                                                // another page once the fetched ones are all on screen.
                                                visibleCounts[platform] = minOf(visible, result.items.size) + VISIBLE_RESULTS_STEP
                                                if (hidden == 0) model.more(platform)
                                            },
                                            enabled = !result.loading,
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("更多${platform.label}结果") }
                                    }
                                }
                            }
                        }
                    }
                    1 -> LazyColumn(
                        state = accountListState,
                        modifier = Modifier.fillMaxSize().padding(padding),
                        contentPadding = PaddingValues(24.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        item { Text("连接你的世界", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        item { Text("用已有平台账号登录，会话留在这台手机。官方 App 的登录状态不会自动同步。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        items(Platform.entries) { platform ->
                            Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) { PlatformMark(platform); Text(platform.label, modifier = Modifier.padding(start = 12.dp), style = MaterialTheme.typography.titleLarge) }
                                    Text(sessions[platform]?.label.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (platform == Platform.XHS) {
                                        Text("使用官方网页会话搜索笔记。", style = MaterialTheme.typography.bodySmall)
                                    }
                                    Button(onClick = { login(platform) }) { Text(accountActionLabel(sessions[platform])) }
                                }
                            }
                        }
                    }
                    else -> SettingsScreen(onOpenAbout = { showAbout = true }, modifier = Modifier.padding(padding))
                }
            }
        }
    }
}

/**
 * One concise label for the account button, shared by the source card and the account list.
 * The wording follows the session state that is shown right above it.
 */
private fun accountActionLabel(status: SessionStatus?): String =
    if (status == SessionStatus.CAPTURED || status == SessionStatus.VERIFIED) "打开账号" else "登录"

@Composable
private fun PlatformMark(platform: Platform) {
    val label = when (platform) { Platform.BILIBILI -> "b"; Platform.ZHIHU -> "知"; Platform.XHS -> "红" }
    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
        Text(label, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 17.sp)
    }
}

@Composable
private fun StatusCard(message: String, action: String, onAction: () -> Unit, alternate: (() -> Unit)?) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onAction) { Text(action) }
                if (alternate != null) TextButton(onClick = alternate) { Text("查看网页") }
            }
        }
    }
}

@Composable
private fun ResultCard(item: SearchItem, onOpen: () -> Unit) {
    Card(onClick = onOpen, shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (item.summary.isNotBlank()) Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.author, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.metric.ifBlank { "查看原文 ↗" }, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
