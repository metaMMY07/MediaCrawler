# 接手入口：OpenScope Android 0.3.0

更新时间：2026-09-15。绝对工作目录：`C:\Users\30622\Documents\ChatGPT\聚合搜索`。

## 当前交付

本轮交付已改名为 OpenScope，移除好问题 hero 与建议内容。搜索列表每个平台初始显示 3 条，More 每次优先从已加载缓冲追加 3 条；返回搜索列表保存滚动位置。Bilibili 的未登录与已登录状态已区分，不再把未登录状态标成匿名或宣称无需登录。

APK 为 `artifacts/OpenScope-0.3.0-arm64-v8a.apk`，10,434,637 bytes（10.43 MB），versionCode 3。Release 使用与旧版相同的内部测试 debug 签名，可覆盖安装；不要写成已安装到用户真机。当前模拟器为 `emulator-5580`，0.3 x86_64 Release 已成功覆盖安装并启动，versionCode 3 / versionName 0.3.0 已核实；主页截图见 `artifacts/openscope-release-home.png`。

先读 [docs/release-0.3.0.md](docs/release-0.3.0.md)，这是最新验收记录；上一版记录保留在 [docs/release-0.2.0.md](docs/release-0.2.0.md)。更早的性能样本见 [docs/build-verified.md](docs/build-verified.md)，不能标成 0.3.0 实测。

## 验证与设备证据

```powershell
pwsh -File .\scripts\build-local.ps1 -Target Verify
```

Verify 成功，耗时 1m46s；31 项 JVM 单测通过、0 失败，lint 为 0 error / 22 warnings。脚本使用配置好的 JDK、Android SDK 与 Gradle wrapper，不运行 clean，也不删除文件。

开发模拟器 Release 强停后单次 `am start -W` 的 TotalTime 为 2080 ms，未达到冷启动目标。Bilibili 搜索实际返回 20 条耗时 3684 ms，仅为一次观察值，不是性能承诺。返回前后同一条卡片 bounds 均为 `[100,295][550,358]`；More 使用已加载缓冲，没有第二次请求。以上均为模拟器证据，尚未完成用户真机安装、真实账号登录或认证后的端到端验收。

## 关键实现

- `ui/AppIcons.kt`、`ui/ThemePreferences.kt`、`ui/Theme.kt`、`ui/SettingsScreen.kt`、`ui/MediaSearchApp.kt`：OpenScope 外观与设置导航。SharedPreferences 只存外观偏好，选择预设关闭系统动态色。
- `core/BrowserProfile.kt`、`ui/PlatformBrowser.kt`、`assets/browser/desktop-viewport.js`：Bili/XHS 官方 WebView 桌面 UA、HTTPS 地址规范化、查询参数保留、1200 CSS px 视口与缩放。必须保留 MATCH_PARENT 的原生布局参数，否则模拟器 CSS 100vh=0，导致小红书侧栏坍塌。脚本不读取表单或 cookie，也不绑定原生桥。
- `session/SessionStore.kt`、`bilibili/BilibiliAdapter.kt`、`SearchViewModel.kt`：账号 scope 扫描、官方认证校验与持久化凭证指纹；Bili `SESSDATA` 与 `nav.isLogin` 联合验证。仅凭 cookie 存在不能宣称已登录。
- 小红书改走官方网页 DOM 搜索路线，不移植私有签名；修复官方 `search_result` 跳到 HTTP 导致白屏的问题，升级 HTTPS 并保留参数，未登录时正确展示 App 内登录入口。真实账号登录/认证后 XHS 卡片与分页仍待用户手机验证，不能声称端到端成功。
- `scripts/inspect-webview.ps1`：仅用于开发模拟器的可见页面检查，ADB/CDP 不属于终端用户流程。

## 后续优先级

1. V1：用户使用已有账号完成 Bili/XHS/知乎登录，验证 cookie 作用域、服务端认证、杀进程、重启、切网和失效恢复。不读取或代填真实凭证；官方挑战交给用户。
2. V2/V5：先在用户手机验证小红书官方网页关键词提交、卡片和分页恢复，再决定是否需要其他取数方案。原生聚合私有签名仍未移植。知乎 3 个签名固定向量通过不等于真实搜索成功。
3. V3：同账号、同查询在家宽和蜂窝网记录成功/挑战/限流。Bili 已有至少 3 秒间隔、60 秒挑战暂停和无自动重试策略；稳定性无保证。
4. V4：目标真机实测新版 APK 安装、冷启动、热态新搜索、内存与耗电。旧性能只见 `docs/build-verified.md`，不要标成新版结果。
5. 需要正式分发时安排私有签名；当前 APK 是内部测试包。不得声称 Cronet 与 Chrome 的所有 TLS 指纹完全相同。

## 必须遵守

- 禁止批量或递归删除文件/目录，不执行 clean。
- 保留 Android 原生、无 PC/Termux/root、Material You、三个平台已有账号的前提。Douyin 已砍。
- 本工作目录文件尚未形成正式提交，不能假称已提交或推送。
- 当前未完成用户真机安装，不能写成已真机安装。
- 若以后委派 dsh，写明绝对目录、范围、验收与产物；按用户提供的新版桥首行、交接记录、桥复核检查，不直接采信代理自述。
