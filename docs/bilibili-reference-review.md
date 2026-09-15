# B站参考实现差异复核（只读）

对象：App 的 `BilibiliAdapter.kt` 与 `.reference/MediaCrawler` 的 Bili light_api 路径。未构建、未改源、未发网络请求；结论均附路径行号。

## 一、搜索参数：一致（事实）

字段逐个相同：`search_type=video`、`keyword`、`page`、`page_size=20`、`order=""`、两个 `pubtime_*_s=0`。App 见 `BilibiliAdapter.kt:56-66`；参考见 `client.py:299-309`，`order` 取自 `field.py:31` 的 `DEFAULT=""`，`pagination.py:97-98`、`core.py:227-234` 同值。两者均 GET（`CronetTransport.kt:120`；`client.py:234`）。WBI 混合表 `BilibiliWbi.kt:16-21` 对 `help.py:37-42`；排序/wts/过滤 `!'()*`/w_rid 见 `BilibiliWbi.kt:39-54` 与 `help.py:55-75`。签名与参数不是差异点。

## 二、请求头与 UA：有差异（事实）

App 只发 `Accept`、`Referer`（`BilibiliAdapter.kt:30-33`），缺 `Origin`；参考发 UA、Cookie、Origin、Referer、Content-Type（`core.py:265-271`、`286-292`）。UA 相反：App 用移动 WebView UA（`CronetTransport.kt:25`），参考用桌面 UA 池随机（`crawler_util.py:103-124`，`core.py:65` 注入）。www→m 跳转放行是 App 专有（`CronetTransport.kt:64-72`），参考无此分支。

## 三、匿名 Cookie bootstrap：结构性差异（事实）

App：仅一次 GET `https://www.bilibili.com/`（`BilibiliAdapter.kt:25,93-100`），只收 HTTP 层 `Set-Cookie`（`CronetTransport.kt:57-58`、`SessionStore.kt:45-53`），不执行 JS，成功后不再重放（`:94,99`）。参考浏览器路径先注入 `stealth.min.js`（`core.py:94-96`）再 goto 首页（`core.py:103-105`），之后取整套浏览器 cookie（`core.py:259-262`）；参考 light_api 不进浏览器，直接复用既有 `session_snapshot` 作 Cookie（`worker.py:299-306`、`440-472`；`core.py:278-297`）。即参考的无浏览器路径不自建匿名会话。另外 App 容忍 nav 的 `-101`（`BilibiliAdapter.kt:149-171`），参考对任何非 0 code 直接抛错（`client.py:173-180`）并先 `pong()` 查 `isLogin`（`core.py:110-113`）：参考没有匿名 nav 通道。

## 四、data 缺 result（待验证）

参考把 `data.result` 缺失当“没有更多”正常收尾（`core.py:238-242`、`pagination.py:114-117`），`request()` 返回 `data.get("data", {})`（`client.py:182`）；App 则抛 PARSE（`BilibiliAdapter.kt:176-177`）。同为 `code=0` 且无 `result`，参考静默 0 条、App 报错。这是与症状最吻合的源码差异，但不能据此推断线上 data 的真实键集。

## 五、v_voucher / 挑战（事实）

在 `.reference` 与 `app/src/main` 全量检索 `v_voucher`、`gaia`、`voucher`：0 命中。App 仅把 HTTP 403、`code=-352` 映射为 CHALLENGE（`BilibiliAdapter.kt:268,281`）；参考仅在文案层区分 `-412/-352/-101`（`client.py:73-78`）。两边都无 v_voucher 回显或重试实现。

## 六、待验证

1) 线上 data 实际键集（主代理抓取中，本复核不猜）。2) m 站 `Set-Cookie` 能否被 CookieManager 送达 `api.bilibili.com`（`CronetTransport.kt:54-58`、`SessionStore.kt:22-24`）。3) 缺 Origin 与移动 UA 是否改变返回形状。4) `code=0` 无 result 时 data 是否携带 v_voucher 或登录提示字段。
