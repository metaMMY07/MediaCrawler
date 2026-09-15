# 集合 0.2.0 验证记录

日期：2026-09-14。此记录覆盖 0.1.0 的界面和官方网页入口结论；旧性能样本仍保留在 `build-verified.md`，没有当作新版性能数据使用。

## 交付

- APK：`artifacts/collection-0.2.0-arm64-v8a.apk`，10,417,254 bytes（10.42 MB / 9.93 MiB）。
- applicationId：`dev.mediasearch`；versionCode：2；versionName：0.2.0。
- SHA256：`b483869823641694ec882c2906cef80d60a6ae4e7e461e636b6fb76933ffee8a`。
- apksigner 校验通过，与 0.1.0 使用相同的内部测试签名，可覆盖安装；当前仍非正式发布签名。

## 本次修改

- 底部导航改成搜索、账号、设置；搜索使用实心指南针，账号使用实心人像。
- 关于移入设置，显示实际应用版本，支持返回设置。
- 设置提供系统动态配色和鼠尾草、海蓝、珊瑚、紫罗兰、青绿五种配色。选择预设会关闭系统动态配色；偏好持久保存，支持系统深浅色。
- Bilibili 和小红书的登录、网页搜索入口、内容链接共用电脑版 WebView。桌面 UA 保留本机 WebView 的 Chromium 版本；支持时同步设置 UA Client Hints，使用 1200 CSS px 宽视口，支持双指缩放。
- Bilibili 移动域名规范化到 www，保留路径与查询参数；小红书首页进入 `/explore`，笔记的 `xsec_token` 等参数原样保留。
- 登录入口自动尝试打开平台自己的登录框；工具栏也有登录按钮。只触发官方 UI，不读取账号密码或代交验证码。
- WebView 显式使用 MATCH_PARENT 布局参数，修复 CSS `100vh` 为 0 导致的小红书侧栏重叠。修复前模拟器实测 `100vh=0`，修复后为 602.67 CSS px，侧栏高度恢复为 530.67 px。
- Bilibili 不再仅凭页面访问就算登录；需存在 SESSDATA，再以官方 nav 的 `isLogin=true` 验证。匿名搜索保持可用。

## 实际验证

- `scripts/build-local.ps1 -Target Verify` 成功：Debug、Release/R8、26 项 JVM 单测（0 failure / 0 error）、lint（0 error / 18 warnings）。日志：`artifacts/build-0.2-final.log`。
- API 35 模拟器安装 x86_64 Release 成功，安装后读取版本为 0.2.0 / code 2；arm64 APK 已做签名校验，未安装到用户真机。
- 设置中选择珊瑚色立即生效，强制停止并重启后保留；覆盖安装 Release 后仍保留。关于页及返回路径正常。证据：`artifacts/v2-settings-coral.png`、`artifacts/v2-settings-release.png`、`artifacts/v2-about-ui.xml`。
- Bilibili 自动展示官方密码/短信登录框，实际触摸首页视频卡片后在同一 WebView 进入 `/video/BV1Vy8r6JE9z/`，渲染电脑版播放器和内容栏。证据：`artifacts/v2-bili-login-final.png`、`artifacts/v2-bili-content-final.png`。
- 小红书 `/explore` 实际展示完整桌面侧栏、内容网格与手机号/验证码输入框。点击可见笔记封面后进入 `/explore/<id>`，保留 `xsec_token`、`xsec_source`，显示详情并要求官方登录。证据：`artifacts/v2-xhs-fixed.png`、`artifacts/v2-xhs-home-final.png`、`artifacts/v2-xhs-note-final.png`。检查没有读取输入框值或记录 token 值。
- 网页截图使用模拟器 1920×1200 / density 240 进行横屏排版检查，完成后已恢复显示尺寸和密度；不是用户真机截图。设置另有默认手机尺寸的实际截图。

## 使用与边界

账号 → 对应平台登录（2 次点击），在官方页面用已有账号输入密码或手机号验证码；操作数由平台登录方式和验证码挑战决定。没有发送短信、输入真实账号或验证完整登录成功，所以本版不能声称解决所有平台风控拦截。

Bilibili 验证成功会返回搜索；小红书保留网页会话，由用户在官方页面确认登录并继续浏览。出现失效或官方挑战时，回到账号入口重新登录，或使用网页工具栏的登录按钮。小红书原生聚合签名仍未接通，搜索提示已更新为继续使用官方电脑版网页；需要登录的内容遵循平台提示。

桌面网页布局由平台控制，手机竖屏初始字体会较小，可双指放大或横屏阅读。新版没有复测热搜索、冷启动、耗电和安装用时，不将网页表单渲染成功等同于账号认证成功。

## 实现依据

- [WebSettings：UA、宽视口、概览与缩放](https://developer.android.com/reference/android/webkit/WebSettings)
- [AndroidX UserAgentMetadata.Builder](https://developer.android.com/reference/androidx/webkit/UserAgentMetadata.Builder)
- [WebViewCompat：document-start script](https://developer.android.com/reference/kotlin/androidx/webkit/WebViewCompat)

后续优先级：用真实已有账号验证 Bilibili / 小红书短信或密码登录与会话恢复；再测真实手机竖横屏、蜂窝网络与搜索；小红书原生签名和知乎真实账号搜索仍属于后续工作。
