# 集合 0.1.0：构建与实测记录

更新：2026-09-14。主代理复核；这是一版可侧载的 Android 验证工程，**尚未完成三平台可用性和性能目标验收**。本文件取代旧环境报告里的“尚无 APK”等历史状态。用户已有三平台账号，不提供注册流程，也不假设官方 App 登录态共享。

## 已交付

- Kotlin + Jetpack Compose + Material 3；Android 12+ 动态配色，深浅主题，旧系统固定配色。最低 Android 10、arm64 手机。
- 原生搜索与分页、每平台独立失败显示、B站 90 秒内存缓存、官方页面入口、知乎本地 WebView 签名、CookieManager 会话管理。
- bundled Cronet，不依赖 Google Play 服务；运行时没有 Python、Node、PC、Termux、root 或云端代抓。ADB、Node 向量生成器只用于开发。
- 三平台账号入口；cookie 保留本地，禁用应用备份，不记录凭证/搜索词。验证码由用户在官方页面完成。

手机 APK：[collection-0.1.0-arm64-v8a.apk](../artifacts/collection-0.1.0-arm64-v8a.apk)

| 产物 | 实测大小 | SHA256 |
|---|---:|---|
| arm64 Release 验证包 | 10,351,074 bytes（10.35 MB / 9.87 MiB） | `a16cf9a422ddd0e0f66f33c975b3ac5b9a6adba2316c59295e9c40aecf1a5df0` |
| x86_64 Release 模拟器包 | 11,044,908 bytes | `91e7df33719697a51c7b4408498b356f57063389dc06c787aeb0a4b6f53fc673` |

Release 启用 R8，目前用 **Android Debug 测试证书** 签名，可作内部侧载验证。APK v2 签名已通过 apksigner 校验，见 [apk-signature.txt](../artifacts/apk-signature.txt)。正式发行需独立私有签名及完整许可整理；原 MediaCrawler 许可已保留在 App 关于页面和 assets/licenses 中。

## 主代理实际运行的检查

`scripts/build-local.ps1 -Target Verify` 最后运行成功，3 分 27 秒：Debug APK、JVM 单测、Debug lint、Release/R8。日志见 [build-verify.log](../artifacts/build-verify.log)。

- JVM：20 项通过，0 失败/错误。其中 B站适配器 11、WBI 2、知乎适配器 7。包括 code=0 且含 v_voucher 时必须报挑战、不能静默空结果或重试签名的回归用例。
- Lint：0 errors / 15 warnings。主要是较新 SDK/依赖版本提示、兼容属性和代码写法建议；未通过关闭 abortOnError 放行错误。
- Android WebView：与固定提交原知乎 JS 生成的 3 个固定随机数签名向量一致。只验证算法移植等价性，不证明实时服务端接受签名。
- Debug APK 已安装并目视检查首页、知乎登录表单、小红书首页/搜索入口。Release APK 已安装并完成进程冷启动测量。
- Release/R8 冒烟实测已加载 Cronet 143 原生库并发送请求，正确显示 B站 CHALLENGE 和官方网页入口，未发生类裁剪/原生加载崩溃。见 [Release 挑战界面](../artifacts/results-release.png)；它不是成功搜索证明。
- 目视验收修复了运行中切换深色模式后状态栏图标不变亮的问题。最终包的 [浅色首页](../artifacts/home-release.png) 和 [深色首页](../artifacts/home-release-dark.png) 已复核；网络冒烟截图在这一仅系统栏改动前采集。

参考提交位于 `.reference/MediaCrawler`，固定 `8773e47`。上游 840 项测试是用户给定事实，不计入本工程的 20 项。dsh 的长构建委托曾超时；本文件只采信主代理实际构建结果。后续 dsh 只读差异报告具有 v0.5.0 交接和桥复核 1/1，见 [bilibili-reference-review.md](bilibili-reference-review.md)；其“v_voucher 尚未处理”是补丁之前的快照。

## 每平台可行性：已有证据与边界

| 平台 | 分级 | 实测证据 | 尚未通过 |
|---|---|---|---|
| Bilibili | 原生取数可行；稳定性需验证 | Cronet + WBI 匿名搜索实取 20 条，首个样本 1,338 ms | 同一轮约 3 秒后第二请求触发 v_voucher；分页、长期成功率和蜂窝网络未验证 |
| 知乎 | 需验证 | 官方短信/密码登录表单在 App 内正常显示；3 个本地签名向量通过 | 未输入真实凭证，d_c0/z_c0 完整性、账号验证接口、在线搜索、失效恢复均未闭环 |
| 小红书 | 需验证；当前移动网页路线不建议作为可用承诺 | 首页可渲染，但显示打开官方 App 的营销内容，无登录表单；搜索 URL 本次返回空白文档 | App 内登录、web_session、签名移植、页内取数全部未通过 |

证据：[知乎表单](../artifacts/zhihu-login-debug.png)、[小红书首页](../artifacts/xhs-login-debug.png)、[小红书搜索空白](../artifacts/xhs-search-debug.png)、[原生探针输出](../artifacts/runtime-probe-debug.txt)。小红书搜索的开发 CDP 检查读到 `readyState=complete`、空 body、`<html><head></head><body></body></html>`；未从这一单次结果推断所有手机都不支持。

B站 www 首页在移动 UA 下转向 m 首页，已增加精确的 HTTPS 首页跳转许可。签名接口跨主机跳转仍不透明跟随。每跳重新按 URL 取 cookie，不转发原请求的显式 cookie 快照。实测第二请求 HTTP 200、code=0 但 data 仅有 v_voucher；现按 CHALLENGE 处理，不泄露 voucher、不尝试绕过验证。

## 操作步骤与登录恢复

- B站正常匿名搜索：输入关键词 → 点搜索，共 2 个应用动作；登录额外步骤为 0。已有账号也可从账号页打开官方页面，是否改善稳定性尚待实测。
- 知乎首次短信登录：① 首页点“登录” ② 填已有账号手机号 ③ 点官方“发送验证码” ④ 在同一手机读取并填写验证码 ⑤ 点官方“登录”。这是当前可见表单的 **5 步计划流程**，还没有用真实账号完成；验证码、安全校验可能增加动作，不能承诺固定总步数。
- 知乎登录完成后，设计为自动检查 cookie、调用账号验证、返回并重试原搜索；没有额外“复制 cookie/同步”按钮。正常会话下重复搜索不应再登录，但该闭环待用户账号验证。
- 知乎失效：显示“在 App 内登录” → 同一官方表单 → 验证通过后返回原搜索。网络故障不直接认定账号失效，其他平台不受影响。
- 小红书尚无已验证的 App 内登录步骤，不能给出虚假的“首次登录后零操作”。本版明确显示尚未接通，只保留尝试官方网页入口。

## 风控与失败可观测性

- UA 使用本机 WebView 默认值；bundled Cronet 版本与 WebView 版本不同，不保证完整 Chrome 指纹相同。未使用随机 UA、代理池或验证码绕过。
- 同平台用户请求启动间隔至少 3 秒；按需分页。挑战/限流后暂停 60 秒，无自动重放；策略目前在内存中，进程重启会重置，下一阶段应持久化退避时间。
- 只缓存 B站结果 90 秒，缓存命中会标注“缓存”；性能报告不拿缓存替代新鲜搜索。知乎未做结果缓存，避免账号变化复用旧结果。
- HTTP 401、403、412/429、业务 -101/-352/-412、v_voucher、缺少必需字段分别映射登录、挑战、限流或解析失败，不伪装“没有结果”。接口变化仍可能需要适配。
- 登录来源为官方 WebView，会话按平台 cookie URL 读取，HTTP Set-Cookie 回写；名称存在只记为“待验证”，不据此宣布登录成功。小红书没有账号验证适配器，始终不标记原生认证成功。

## 性能：实测与验收口径

环境：API 35 Google APIs x86_64 模拟器、2 核/2 GB、1080×2400、软件 GPU、宿主 NAT；WebView 124、Cronet 143。状态栏 3G 图标不是实蜂窝网络。以下 **全部是模拟器实测，不是目标手机估算**。

| 指标 | 实测 | 结论 |
|---|---|---|
| arm64 Release 包体 | 10.35 MB | 达到十兆级包体；arm64 真机安装未测 |
| x86_64 更新安装 | 4.743 秒 | 最终包 ADB `install -r` 单次计时，含传输；不能代表手机系统安装器首次安装 |
| Release 进程冷启动 TTID，5 次 | 2535 / 1835 / 1522 / 1529 / 1279 ms | p50 1529 ms，p95 最近秩 2535 ms；本环境未达 <500 ms；不是输入可响应性测量 |
| B站新鲜搜索，Debug 原生探针 | 首个成功 1338 ms / 20 条 | 未达 <1 秒；没有足够成功样本计算 p50/p95 |
| 同轮第二次 B站请求 | 156 ms / 挑战失败 | 不是成功的热搜索时间；本轮停止，第三个样本未发送 |
| 知乎离线签名，Debug | 初始化 894 ms；3 次调用 157 / 29 / 4 ms | 测算法运行成本，非搜索耗时；首次初始化在首次需要签名时发生 |
| Release 首页内存快照 | TOTAL PSS 36,083 KB，RSS 136,816 KB，WebViews=0 | 约 35.2 MiB PSS；不含打开登录页面/搜索后的峰值 |
| 真机耗电/蜂窝成功率 | 未测 | 不提供推算结论 |

冷启动这里是 force-stop 后启动，系统磁盘缓存保留，不是重启手机的首次启动。5 个样本统计效力有限，不能外推手机。原始文件：[metrics.json](../artifacts/release-emulator-metrics.json)、[meminfo](../artifacts/release-emulator-meminfo.txt)。

## 复现

```powershell
pwsh -File .\scripts\build-local.ps1 -Target Verify
pwsh -File .\scripts\build-local.ps1 -Target Probe
pwsh -File .\scripts\start-emulator.ps1
# 等专用 emulator-5580 的 sys.boot_completed 返回 1
pwsh -File .\scripts\measure-emulator.ps1 -Samples 5
```

签名探针：先安装 Debug 主 APK 和 `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`，执行 `adb -s emulator-5580 shell am instrument -w dev.mediasearch.test/dev.mediasearch.RuntimeProbe`。默认仅离线签名校验；`-e live true` 才发送有限的 B站真实请求。挑战后立即停止，不用批量重试获得漂亮数据。探针通过看 JSON 的 `probe_completed` 和 `signature_vectors_passed`；仅看 adb 进程退出码不够。记录中的 probe_completed=false 是真实请求遇到挑战，3 个离线签名向量仍通过。

工具链：JDK 17、Gradle 8.13、AGP 8.12.1、Kotlin 2.2.10、compile/targetSdk 35。Windows 中文路径通过已核对目标的 ASCII junction 构建；脚本支持传入 JDK/SDK，不做 clean/删除。专用 AVD 位于 `C:\Users\30622\.codex\mediasearch-avd`，名 MediaSearchApi35，镜像 `system-images;android-35;google_apis;x86_64`。不使用其他项目设备。

## 接下来按风险优先级推进

1. **P0 / V1：真实账号闭环。** 先在目标 arm64 手机验证知乎已有账号 5 步流程，记录必需 cookie 是否齐全（只记录布尔值）、账号验证与首个搜索，再测进程重启/到期恢复。小红书另测官方 HTTPS 登录入口是否存在可用移动流程；不先承诺桌面模式能解决。
2. **P0 / V2 / V5：小红书限时技术探针。** 验证官方页面里签名/取数的合法可访问执行环境，或对 xhshow 建立输入输出向量后独立移植。不新增 Python 运行时。登录或签名任何一步失败，原生聚合保持关闭。
3. **P1 / V3：网络与稳定性。** 同一手机/账号，在蜂窝与家宽用相同关键词集合和节奏对照；每格报告尝试数、成功/挑战/限流比例、HTTP/业务失败分类。挑战即停，后续经官方流程恢复；不由一次成功推断稳定。
4. **P1 / V4：手机指标。** Release 启动不少于 20 次，区分进程冷/热与缓存；新鲜搜索按平台分别记录首项/首屏/全部平台完成 p50/p95，失败单独统计。安装用手机系统安装器录像，记录型号/系统/存储/网络；内存含 WebView 子进程，耗电用固定时间/次数对照。
5. **V6：正式 APK 无 Python/ExecJS，因此不适用。** 若保留 Python 开发基准，使用 PYTHONUTF8=1 单独复测，不能把开发依赖带入手机。

小红书的降级顺序：原生结构化搜索 → 验证通过的页内取数 → 验证通过的官方网页搜索 → 未通过时保留明确提示或关闭该平台。本次已经观察到移动网页限制，**不能把第三档当成必然可用**。若签名无法移植而页内/网页也失败，就只提供 B站及验证通过后的知乎聚合，小红书不显示伪造结果；用户将失去在同一列表搜索小红书的能力。跳转官方 App 只能作为另行确认的体验变更，不能冒充原定 App 内完成。
