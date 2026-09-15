# OpenScope 0.3.0 验收记录

日期：2026-09-15。此记录区分代码验证、模拟器实际操作和仍待真实账号验证的行为。

## 安装包

- 文件：[OpenScope-0.3.0-arm64-v8a.apk](../artifacts/OpenScope-0.3.0-arm64-v8a.apk)
- 大小：10,434,637 bytes，10.43 MB（9.95 MiB）。
- applicationId：`dev.mediasearch`；versionName：`0.3.0`；versionCode：`3`；minSdk：29。
- APK SHA-256：`0BF417A24FC8191988CECB3A0CE7822823CC70A090190D8653B82F054F352FAB`。
- apksigner 验证通过，证书 SHA-256：`583079a20081a1bedaf8c1fbdf93e92bf3cc3544e0f7246fef7b1625d9066a02`。与旧版相同的内部测试 debug key，可覆盖安装。不是正式分发签名。
- x86_64 Release 已在 API 35 模拟器 emulator-5580 覆盖安装、启动，并核对版本 3 / 0.3.0；arm64 包尚未在用户手机安装验收。

## 本轮改动

1. 应用名称、主标题及关于页面统一为 OpenScope。删除“从一个好问题开始”、示例问题和相关首页宣传文案。保留 Material You 主题、搜索/账号/设置导航。
2. 每个来源初始显示 3 条，每次“更多”增加 3 条。先展示已获取的结果，缓冲用完才请求下一页。新搜索重置数量，浏览内容返回不重置。
3. 搜索和账号列表各自保存滚动状态。网页“返回”处理网页历史，“关闭”回到原列表；退出不再等待异步网页验证。
4. Bilibili 使用简洁的未登录/已登录状态，移除匿名搜索、无需登录的拥挤提示。公开搜索能力保持可用。
5. 会话检测同时检查平台 cookie 地址与首页的作用域；所有官方内容浏览页面均可触发会话确认。已确认凭证的 SHA-256 指纹持久化，用于恢复状态；不会把 cookie 名称存在、普通搜索成功直接当作认证成功。确认依赖官方认证响应或页面认证状态，并要求必要凭证可读。
6. 小红书新增官方网页搜索结果读取路线，不再固定返回“不支持”。使用官方页面完成请求，读取与当前关键词及官方 search.feeds ID 对应的笔记卡片；保留原链接的安全参数。认证和搜索使用独立 WebView，避免启动验证阻塞搜索或破坏分页。
7. 实际发现小红书搜索会尝试跳转到 HTTP 的 search_result 地址。对允许的官方地址升级到 HTTPS 后继续，保留查询参数，仍拒绝外站和非白名单导航；没有开放明文流量。

## 已完成验证

- `scripts/build-local.ps1 -Target Verify`：Debug、Release/R8、JVM 单测、Debug lint 全部成功；最终日志 [build-0.3-final.log](../artifacts/build-0.3-final.log)，构建耗时 1m 46s。
- 31 项 JVM 测试：BilibiliAdapter 13、BilibiliWbi 2、BrowserProfile 5、SessionEvidence 2、XhsPageClient 2、ZhihuAdapter 7；0 失败、0 错误。
- lint：0 error、22 warnings。报告保存在 `app/build/reports/lint-results-debug.xml`。
- 实际触摸打开网页后关闭，结果“Android零基础入门课程”的位置前后均为 `[100,295][550,358]`。证据：[返回前 XML](../artifacts/v3-xhs-result.xml)、[返回后 XML](../artifacts/after-close.xml)。
- 实际点击“更多”显示缓冲中的后续结果，未产生第二次 Bilibili 请求。证据：[展开后 XML](../artifacts/after-more.xml)。
- 新版实际搜索 Android，Bilibili 返回 20 条，单次请求记录 3684 ms；UI 初始仅显示 3 条。知乎和小红书在没有真实账号的模拟器上均返回 LOGIN_REQUIRED。小红书没有再返回固定 unsupported、白屏或本轮此前修正的空 URL 异常。实际日志见 [search-0.3-samples.log](../artifacts/search-0.3-samples.log)。
- 小红书可见官方搜索页实际显示“登录后查看搜索结果”和手机号登录框，页面认证状态为 false。未输入真实凭证、未发送短信。
- 主代理亲自检查了[本轮界面截图](../artifacts/openscope-final.png)：OpenScope 标题、更多按钮、知乎/小红书 App 内登录入口均可见。
- 覆盖安装后的 [Release 首页截图](../artifacts/openscope-release-home.png) 已亲自检查：三平台均显示简洁的未登录/登录，示例问题区域已移除。
- Release 模拟器强停后单次 `am start -W`：COLD，TotalTime 2080 ms、WaitTime 2128 ms。这是模拟器 Activity 启动样本，不是目标真机首屏验收；没有达到或证明冷启动 <0.5s。搜索样本也没有达到 <1s。未测量新版真机安装耗时、耗电和稳定延迟分布。

曾尝试的 UiRegressionProbe 触摸自动化不稳定，未作为通过证据；本轮新增的该测试文件已单文件移除，保留诊断日志。返回位置和更多行为以以上实际触摸及 XML 为准。

## 仍需验证的边界

小红书真实账号登录后的结果读取与分页尚未端到端验证。官方页面 DOM 和 search.feeds 结构可能变化；当前适配遇到未知布局、登录失效或挑战会展示登录/错误及官方页面入口，不会用推荐流替充搜索结果。此版本没有移植小红书私有签名。

三平台真实账号完成登录后状态恢复、杀进程重启、切换网络和失效恢复仍需在用户手机验证。此次修复了会话同步与状态判断的代码路径，但不能仅凭模拟器未登录测试宣称“登录后显示未登录”已在真实账号上彻底解决。

后续优先验证：已有账号 App 内登录 → 返回账号页确认 → 搜索显示每源 3 条 → 更多 → 内容返回位置 → 杀进程重新打开 → 切网。小红书另核对搜索词、结果 ID、链接安全参数和分页去重。遇到验证码由用户在官方页面完成，应用不自动重试挑战。

## 协作记录

dsh 的 UI 修改经主代理读取源码、集成和实际测试复核；原工具调用超时，不能把其文本报告称为已完成桥复核。Luna 的小型代码审阅发现的认证确认和小红书结果筛选问题已修正。README 与 HANDOFF 由 Luna 更新，最终构建、签名及截图由主代理核对。
