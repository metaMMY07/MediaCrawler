# UI 0.3 交接报告（品牌改名 / 列表滚动 / 平台结果分页 / 来源卡）

受指派范围：UI 层（`MediaSearchApp.kt`、`SettingsScreen.kt`、`res/values/strings.xml`、`AndroidManifest.xml` 的 app label 文案）
工作目录：`C:\Users\30622\Documents\ChatGPT\聚合搜索`（构建走 ASCII junction `C:\Users\30622\.codex\mediasearch-workspace`）
报告版本：rev2 — 对齐 19:29 的最终代码（分页计数改为 `state.searchId`），并附 19:30–19:36 的独立复核证据
日期：2026-09-14

---

## 0. 范围与边界

### 0.1 本报告覆盖的产物（当前磁盘状态，sha256 为实测）

| 文件 | 角色 | sha256 |
| --- | --- | --- |
| `app/src/main/java/dev/mediasearch/ui/MediaSearchApp.kt` | 顶栏/首页/搜索列表/账号列表/分页/来源卡 | `25DBDFD380CF4C4414A887CEB120A11822D54621A63F24CD20605EB9EA56C31D` |
| `app/src/main/java/dev/mediasearch/ui/SettingsScreen.kt` | 设置页与关于页文案 | `0AA5347B0AC6B71AEB330F52C80586480C23EBF54657D9DE807BFD0ADC57F1A1` |
| `app/src/main/res/values/strings.xml` | `app_name` | `BD7F61BBB46BC670E0CF74F9C661DA7960186543F9F68D06F60EA5A947D704FD` |
| `AndroidManifest.xml` | **未改动**（label 已是 `@string/app_name`） | `—` |

### 0.2 按要求未触碰

`SearchViewModel.kt`、`SessionStore.kt`、`PlatformBrowser.kt`、网络层（`CronetTransport`）、适配器（`BilibiliAdapter` / `ZhihuAdapter` / `XhsPageClient`）、`applicationId`、小红书适配逻辑。

### 0.3 并发写入提醒（重要）

同一工作目录在 19:22–19:29 期间被多个写入者改动，实测 mtime：

- `ui/SettingsScreen.kt` 19:22:01、`res/values/strings.xml` 19:22:02、`ui/PlatformBrowser.kt` 19:24:16、`SearchViewModel.kt` 19:27:10（新增 `searchId`）、`ui/MediaSearchApp.kt` 19:27:29 然后 19:29:00（分页计数改为 `remember(state.searchId)`）。
- 因此本报告**只对上表 sha256 对应的磁盘内容负责**；若主代理在 19:29 之后再次改写这些文件，第 6 节的复算命令需要重跑（`artifacts/ui-0.3-assertions.ps1` 末尾会打印每个文件的 sha256）。

---

## 1. 品牌改名：用户可见名称 → OpenScope

| 位置 | 当前值 | 验证方式 |
| --- | --- | --- |
| 顶栏标题（非设置页） | `OpenScope` | `MediaSearchApp.kt` 断言 1.2 |
| 顶栏图标 `contentDescription` | `OpenScope` | 断言 1.3 |
| 设置页条目 | `关于 OpenScope` | 断言 1.6 |
| 关于页大标题 | `OpenScope` | 断言 1.7 |
| 应用名 `app_name` | `OpenScope` | 断言 1.1 + APK 实测 |
| `applicationId` | `dev.mediasearch`（未改） | 断言 1.9 + APK 实测 |

改名前基线（aapt2 实测）：`artifacts/collection-0.2.0-arm64-v8a.apk` → `application-label:'集合'`；
改名后（19:28 构建的 0.3.0 debug APK）→ `application-label:'OpenScope'`。原始输出见 `artifacts/ui-0.3-aapt-labels.txt`。

未改：底部导航「搜索 / 账号 / 设置」、关于页正文、`BuildConfig.VERSION_NAME`、包名、`Theme.MediaSearch` 样式名。
`AndroidManifest.xml` 无需改动：`<application>` 与 `<activity>` 的 `android:label` 都指向 `@string/app_name`。

## 2. 首页精简（`MediaSearchApp.kt`）

删除（断言 2.1–2.6 全部为 0 命中）：

- Hero 组合函数与调用（`把世界放进搜索框`、`好奇心，不止一个答案。` 及 Canvas 插画）；
- `从一个好问题开始` 标题与 `SuggestionChip` 建议词（城市漫游 / 咖啡入门 / 摄影构图）；
- 底部标语 `一次搜索，发现不同视角。`。

保留（断言 2.7–2.9）：搜索框（含搜索按钮 / IME Search 动作）、来源筛选 `FilterChip` 行、`你的内容来源` 卡片。
顺带清理：删除随 Hero 一起失效的 import（`Canvas`、`geometry.Offset`、`drawscope.Stroke`）与本就未使用的 `CircleShape` / `Color`；`sp` 仍被 `PlatformMark` 使用故保留。

## 3. 列表滚动位置（条件分支外 hoist）

`MediaSearchApp` 顶部（`CollectionTheme` 与其 `if (browser != null) / else if (showAbout) / else` 分支之外）：

```kotlin
val searchListState = rememberLazyListState()   // 搜索列表
val accountListState = rememberLazyListState()  // 账号列表
```

- 搜索列表 `LazyColumn(state = searchListState, …)`，账号列表 `LazyColumn(state = accountListState, …)`：两个位置互相独立（断言 3.1–3.4）。
- 打开结果详情（内置浏览器）或关于页时整个 `Scaffold` 离开组合树；hoist 到 `MediaSearchApp` 自身后返回仍保持位置，切 Tab 同理。
- **新搜索回顶**：只有用户发起搜索的入口 `startSearch()` 里执行 `scope.launch { searchListState.scrollToItem(0) }`；搜索框按钮与 IME Search 都调它（断言 3.5、3.6：`scrollToItem(0)` 与 `model.search()` 各仅 1 处）。打开/返回详情不经过该函数，因此不回顶、也不重置条数。
- 回顶是命令式的，不用 `LaunchedEffect(key)`，所以从详情返回重新进入组合时不会被误触发（文件内 `LaunchedEffect` 计数为 0，断言 4.11）。

## 4. 每个平台先展示 3 项、每次「更多」再放 3 项

```kotlin
private const val INITIAL_VISIBLE_RESULTS = 3
private const val VISIBLE_RESULTS_STEP = 3

val visible = visibleCounts[platform] ?: INITIAL_VISIBLE_RESULTS
val shown   = result.items.take(visible)
val hidden  = result.items.size - shown.size
…
items(shown, key = { "${platform.name}-${it.id}" }) { … }
if (shown.isNotEmpty() && (hidden > 0 || result.hasMore)) {
    item(key = "more-${platform.name}") {
        OutlinedButton(
            onClick = {
                visibleCounts[platform] = minOf(visible, result.items.size) + VISIBLE_RESULTS_STEP
                if (hidden == 0) model.more(platform)   // 已抓取的都上屏了，才真的取下一页
            },
            enabled = !result.loading, …) { Text("更多${platform.label}结果") }
    }
}
```

- **先本地、后网络**：`hidden > 0` 时点击只做本地展开，`model.more` 不调用 → 不产生额外抓取；只有 `hidden == 0 && hasMore` 才请求下一页（断言 4.3–4.7）。
- **页到达即见**：点击时先 `+3`，请求返回后新条目立即落进 `shown`，不需要二次点击。
- **无自动抓取**：结果到达后没有任何 `LaunchedEffect` / 自动分页（断言 4.11）。
- **分页中**：`enabled = !result.loading`，避免重复请求。
- **分页失败**：`SearchViewModel.load()` 失败分支用 `old.copy(message = …)` 保留 `items`，`shown` 由 `result.items` 派生，已展示项不消失；「更多」按钮仍在（`hidden > 0 || hasMore` 未变）。

### 计数重置语义

```kotlin
val visibleCounts = remember(state.searchId) { mutableStateMapOf<Platform, Int>() }
```

`SearchState.searchId` 由主代理加入，`search()` 每次都写 `searchId = token`（`generation++`，`SearchViewModel.kt:80`），因此：

- 新查询 → `searchId` 变 → map 重建 → 每平台回到 3 项；
- **相同查询重复搜索** → `generation` 同样递增 → 同样回到 3 项；
- 点「更多」/重试/打开详情再返回 → `searchId` 不变 → 计数保留；
- `visibleCounts` 位于条件分支之外，浏览器/关于页返回时不会被重建。

> 说明：本文件早先的 rev1 用的是 UI 本地 `searchRound` 计数器；`searchId` 落地后已替换为上面的写法（rev2），行为等价且少一处本地状态。

## 5. 来源卡与账号区

- 来源卡删除 `匿名搜索` 角标、`无需登录` 类文案、小红书的 `网页入口 · 待验证` 特例（断言 5.1–5.3）。
- 三行**统一**使用 `sessions[platform]?.label.orEmpty()`（`SessionStore` 的状态文字）——来源卡与账号列表各 1 处（断言 5.4）。`GUEST`/`MISSING` 由主代理统一为「未登录」、`VERIFIED` 为「已登录」，UI 无需再改。
- 登录/打开账号按钮统一为一个函数，来源卡 `TextButton` 与账号页 `Button` 共用（断言 5.5、5.6）：

```kotlin
private fun accountActionLabel(status: SessionStatus?): String =
    if (status == SessionStatus.CAPTURED || status == SessionStatus.VERIFIED) "打开账号" else "登录"
```

替换了原来的 `登录哔哩哔哩` / `官方网页版登录` / `登录${platform.label}` / `打开账号页面` 四种写法。
账号页小红书补充说明现为 `使用官方网页会话搜索笔记。`（原 `官方网页版登录；原生聚合尚未接通。` 在 `XhsPageClient` 落地后已过期）。

---

## 6. 验证（实际执行过的命令与结果）

所有验证都通过 ASCII junction `C:\Users\30622\.codex\mediasearch-workspace` 进行（本机非 ASCII 路径会让 JDK 命令行参数解码坏掉）。

### 6.1 Kotlin 全量编译（前端 + 后端 codegen）— 0 error

```
java -Xmx2560m -cp <kotlin-compiler-embeddable-2.2.10.jar;annotations-13.0.jar;kotlinx-coroutines-core-jvm-*.jar;kotlin-stdlib-2.2.10.jar> \
     org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
     -no-stdlib -classpath <427 jars + android-35/android.jar + R.jar + 生成的 BuildConfig classes> \
     -Xplugin=<kotlin-compose-compiler-plugin-embeddable-2.2.10.jar> -jvm-target 17 -d <out> <app/src/main/java 下 19 个 .kt>
```

- **exit code 0**，13.3 s，**error 0 条**，warning 1 条（`SearchViewModel.kt:130:21 'when' is exhaustive so 'else' is redundant here.`——与主代理 19:27 Gradle 构建日志中的同一条警告一致，属既有代码），产出 **124 个 .class**。
- 编译器/插件/classpath 与工程一致（Kotlin 2.2.10 + compose plugin 2.2.10，来自 `D:\CodexToolchains\gradle\caches` 与 `.gradle-user-home` 依赖缓存；`android.jar` = API 35；`R.jar` 与 `BuildConfig.class` 取自 `app/build` 产物）。
- 日志：`artifacts/ui-0.3-typecheck.log`；可复现脚本：`artifacts/ui-0.3-typecheck.ps1`（每次写入新的 `out-<hhmmss>` 目录，不删除任何文件）。
- **这不是 AGP/Gradle 构建**：没有资源链接、D8/R8、APK 打包与 AGP 字节码变换。第 7 节如实标注。

### 6.2 需求级断言扫描 — 41/41 PASS

```
& artifacts\ui-0.3-assertions.ps1        # 只读：grep 源码 + 打印各文件 sha256
SUMMARY: pass=41 fail=0
```

输出存档：`artifacts/ui-0.3-assertions.txt`（覆盖第 1–5 节全部条目，含「0 命中」类负向断言）。

### 6.3 分页/可见条数纯逻辑单测 — 15/15 PASS

`artifacts/ui-0.3-logic-test.js` 通过 `dev_page_check({js})` 在本地 vm 执行（无浏览器）：

- 初始 3 项、首屏 20 项时隐藏 17 项；连点「更多」把 20 项全部放出且**全程 0 次** `model.more`；缓冲用尽时**恰好 1 次** `model.more`；返回页立即可见（23 项）；失败后已展示项与按钮都在；`searchId` 变化回到 3、返回详情保持 9；1 条无更多时按钮不出现；0 条时不出现取数按钮。
- 结果存档：`artifacts/ui-0.3-logic-test-result.json`。

### 6.4 应用名（真实产物）— aapt2 实测

```
aapt2 dump badging artifacts\collection-0.2.0-arm64-v8a.apk              -> application-label:'集合'
aapt2 dump badging app\build\outputs\apk\debug\app-arm64-v8a-debug.apk   -> application-label:'OpenScope'  (package dev.mediasearch, versionName 0.3.0, versionCode 3)
aapt2 dump badging app\build\outputs\apk\debug\app-x86_64-debug.apk      -> application-label:'OpenScope'
```

原始输出：`artifacts/ui-0.3-aapt-labels.txt`。
注意：该 APK 由 19:27–19:28 的构建产出，**早于** 19:29 的分页计数修订，因此它只证明「改名 + 包名不变」，不能当作最终代码的编译凭据（编译凭据见 6.1）。

## 7. 未验证 / 存疑

- **未运行 Gradle/AGP 构建**（按指派由主代理构建）：没有 `assembleDebug`/lint/单元测试；6.1 的手工编译**不等于**可打包。最终代码（`25DBDFD3…`）尚未进入任何 APK。
- **未做任何视觉/交互验收**：无截图、无模拟器操作。滚动保持、回顶时机、「更多」的手感、来源卡排版、`OpenScope` 在窄屏/大字体下是否被截断（`letterSpacing = 3.sp` 沿用）均为**未验证**。
- **`visibleCounts` 是 `remember` 而非 `rememberSaveable`**：进程被杀后回到 3 项。`MainActivity` 已声明 `configChanges`（旋转不重建 Activity），所以旋转应保留；未实测。
- **分页失败保留已展示项**由代码路径推理 + 6.3 的逻辑单测支持，未用真实失败响应在设备上复现。
- **`accountActionLabel` 依赖枚举常量名**（`CAPTURED`/`VERIFIED`/`MISSING` 只用于文案判断）：若主代理重命名 `SessionStatus` 常量，需同步改 `MediaSearchApp.kt` 该行。
- **关于页正文与代码可能不一致（请主代理裁定）**：`SettingsScreen.kt` 关于页仍写「小红书尚未接通，暂留官方网页入口。」，但 `SearchViewModel` 已接入 `xhs/XhsPageClient.kt`（19:23/19:27 新增）。该句属小红书适配范围，**本代理未改**，若已接通请主代理更新文案。
- **小红书结果如何走进分页语义**未验证：`XhsPageClient.search(term, page)` 是否真的返回可翻页的 `hasMore`，未读其实现细节与真机表现。
- **会话状态文字**：`sessions[platform]?.label.orEmpty()` 在会话未加载时为空字符串（不显示兜底文案）；主代理负责的会话加载落地后应显示「未登录/已登录」等。空态表现未验证。
- **并发写入风险**：见 0.3。若 19:29 之后有人再改 `MediaSearchApp.kt`，第 6 节结论作废，需重跑 `artifacts/ui-0.3-assertions.ps1`（会打印当前 sha256）与 `artifacts/ui-0.3-typecheck.ps1`。

## 8. 给主代理的验收清单

1. `scripts/build-local.ps1 -Target Debug`（或 `:app:assembleDebug`）：确认最终修订编译/打包通过（本代理只证明了 Kotlin 编译，未跑 AGP）。
2. `aapt2 dump badging app\build\outputs\apk\debug\app-arm64-v8a-debug.apk`：`application-label:'OpenScope'`，`package: dev.mediasearch`。
3. 首页自上而下应为：搜索框 → 筛选行 → 来源卡；**不应**出现 Hero、`从一个好问题开始`、建议词、`一次搜索，发现不同视角。`。
4. 搜索一个词：每平台最多 3 条 + 「更多X结果」；首屏抓到 >3 条时第一次点击只本地展开（可用 logcat/网络面板确认无新请求）；缓冲用尽后再点才取下一页。
5. 滚到列表中部 → 点开结果 → 返回：位置保持；再点搜索（同词或新词）：回顶且每平台重新只显示 3 条。
6. 来源卡不应出现「匿名搜索」；状态文字来自会话状态；按钮文案只有「登录」或「打开账号」。
7. 裁定并（如需要）更新关于页「小红书尚未接通」一句（见第 7 节）。
