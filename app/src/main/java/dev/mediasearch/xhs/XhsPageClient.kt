package dev.mediasearch.xhs

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.*
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import dev.mediasearch.core.*
import dev.mediasearch.session.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder

/** User-triggered official-page search; no private signing implementation or challenge retries. */
class XhsPageClient(private val context: Context, private val sessions: SessionStore) {
    private val lock = Mutex()
    private var view: WebView? = null
    private var query: String? = null
    private var loaded = false
    private var failure: PlatformException? = null
    private val delivered = mutableSetOf<String>()

    suspend fun verifySession(): Boolean? = lock.withLock {
        withContext(Dispatchers.Main) {
            try {
                withTimeoutOrNull(12_000) {
                    close(); loaded = false; failure = null
                    view = create()
                    view!!.loadUrl("https://www.xiaohongshu.com/explore")
                    while (true) {
                        failure?.let { throw it }
                        if (loaded) PageSessionProbe.authenticated(view!!, Platform.XHS)?.let { return@withTimeoutOrNull it }
                        delay(500)
                    }
                    @Suppress("UNREACHABLE_CODE") null
                }
            } finally { close() }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = BrowserProfile.userAgent(Platform.XHS, WebSettings.getDefaultUserAgent(context))
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val version = Regex("Chrome/([0-9.]+)").find(settings.userAgentString)?.groupValues?.get(1)
            val metadata = UserAgentMetadata.Builder().setMobile(false).setPlatform("Linux").setPlatformVersion("")
                .setArchitecture("x86").setBitness(64).setModel("").setWow64(false)
            if (version != null) metadata.setFullVersion(version).setBrandVersionList(listOf("Chromium", "Google Chrome").map { brand ->
                UserAgentMetadata.BrandVersion.Builder().setBrand(brand).setMajorVersion(version.substringBefore('.')).setFullVersion(version).build()
            })
            WebSettingsCompat.setUserAgentMetadata(settings, metadata.build())
        }
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = true
        layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        measure(android.view.View.MeasureSpec.makeMeasureSpec(1200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(900, android.view.View.MeasureSpec.EXACTLY))
        layout(0, 0, 1200, 900)
        val viewport = context.assets.open("browser/desktop-viewport.js").bufferedReader().use { it.readText() }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(this, viewport, setOf("https://www.xiaohongshu.com"))
        }
        CookieManager.getInstance().setAcceptCookie(true)
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String?, icon: Bitmap?) { loaded = false }
            override fun onPageFinished(v: WebView, url: String?) { loaded = true }
            override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) {
                    val secure = BrowserProfile.secureNavigationUrl(Platform.XHS, request.url.toString())
                    if (secure != null && secure != request.url.toString()) { v.loadUrl(secure); return true }
                }
                val blocked = if (request.isForMainFrame) !BrowserProfile.allowed(Platform.XHS, request.url.toString()) else request.url.scheme != "https"
                if (blocked && request.isForMainFrame) {
                    if (dev.mediasearch.BuildConfig.DEBUG) android.util.Log.d("PageNavigation", "blocked ${request.url.scheme}://${request.url.host}${request.url.path}")
                    failure = PlatformException(FailureKind.CHALLENGE, "小红书要求在官方页面继续验证，请完成后返回重试")
                }
                return blocked
            }
            override fun onReceivedSslError(v: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel(); failure = PlatformException(FailureKind.NETWORK, "小红书网页证书验证失败")
            }
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) failure = PlatformException(FailureKind.NETWORK, "小红书网页加载失败，请稍后重试")
            }
            override fun onReceivedHttpError(v: WebView, r: WebResourceRequest, response: WebResourceResponse) {
                if (r.isForMainFrame) failure = PlatformException(
                    if (response.statusCode == 429) FailureKind.RATE_LIMITED else FailureKind.NETWORK,
                    "小红书网页暂不可用（HTTP ${response.statusCode}）")
            }
            override fun onRenderProcessGone(v: WebView, detail: RenderProcessGoneDetail): Boolean {
                failure = PlatformException(FailureKind.NETWORK, "小红书网页进程已结束，请重新搜索")
                v.destroy(); if (view === v) view = null
                return true
            }
        }
    }

    suspend fun search(term: String, page: Int): SearchPage = lock.withLock {
        withContext(Dispatchers.Main) {
            try {
                withTimeout(16_000) {
                    if (page == 1 || query != term || view == null) {
                        close(); view = create(); query = term; delivered.clear(); loaded = false; failure = null
                        view!!.loadUrl("https://www.xiaohongshu.com/search_result?keyword=${URLEncoder.encode(term, "UTF-8")}&source=web_explore_feed")
                    } else {
                        evaluatePage(view!!, "window.scrollTo(0,document.documentElement.scrollHeight); 'scrolled'")
                    }
                    val script = context.assets.open("browser/xhs-results.js").bufferedReader().use { it.readText() }
                    while (true) {
                        failure?.let { throw it }
                        val web = view ?: throw PlatformException(FailureKind.NETWORK, "小红书网页已关闭")
                        val currentUrl = web.url
                        if (loaded && currentUrl != null) {
                            if (android.net.Uri.parse(currentUrl).path?.trimEnd('/') != "/search_result") {
                                throw PlatformException(FailureKind.LOGIN_REQUIRED, "请先在小红书官方页面登录，再返回重试搜索")
                            }
                            val data = decode(evaluatePage(web, script))
                            if (data.optBoolean("challenge")) throw PlatformException(FailureKind.CHALLENGE, "小红书要求验证，请在官方页面完成后重试")
                            val authenticated = PageSessionProbe.authenticated(web, Platform.XHS)
                            if (authenticated == true && sessions.scan(Platform.XHS)) {
                                sessions.mark(Platform.XHS, SessionStatus.VERIFIED)
                            }
                            if (authenticated == false && data.optBoolean("loginVisible")) {
                                sessions.mark(Platform.XHS, SessionStatus.MISSING)
                                throw PlatformException(FailureKind.LOGIN_REQUIRED, "登录小红书后，即可在这里查看搜索结果")
                            }
                            val matchesQuery = android.net.Uri.parse(currentUrl).getQueryParameter("keyword") == term
                            val batch = if (authenticated == true && matchesQuery) parse(data, delivered) else emptyList()
                            if (batch.isNotEmpty()) {
                                delivered.addAll(batch.map { it.id })
                                return@withTimeout SearchPage(batch, data.optBoolean("hasMore", true), page)
                            }
                            if (authenticated == true && matchesQuery && (data.optBoolean("empty") || !data.optBoolean("hasMore", true))) {
                                return@withTimeout SearchPage(emptyList(), false, page)
                            }
                        }
                        delay(500)
                    }
                    @Suppress("UNREACHABLE_CODE") error("unreachable")
                }
            } catch (e: CancellationException) { close(); throw e }
            catch (e: Exception) { close(); throw e }
        }
    }

    fun close() { view?.stopLoading(); view?.destroy(); view = null; query = null }

    companion object {
        internal fun decode(raw: String): JSONObject {
            val value = JSONTokener(raw).nextValue()
            return if (value is String) JSONObject(value) else value as? JSONObject ?: JSONObject()
        }
        internal fun parse(data: JSONObject, excluded: Set<String> = emptySet()): List<SearchItem> {
            val rows = data.optJSONArray("items") ?: return emptyList()
            return (0 until rows.length()).mapNotNull { i ->
                val row = rows.optJSONObject(i) ?: return@mapNotNull null
                val url = row.optString("url")
                if (!BrowserProfile.allowed(Platform.XHS, url)) return@mapNotNull null
                val path = java.net.URI(url).path
                val id = Regex("/(?:explore|search_result)/([a-fA-F0-9]{24})(?:/)?$").find(path)?.groupValues?.get(1)
                    ?: return@mapNotNull null
                if (id in excluded || row.optString("title").isBlank()) return@mapNotNull null
                SearchItem(id, Platform.XHS, row.optString("title"), row.optString("author"), "", url,
                    row.optString("thumbnail"), row.optString("metric"))
            }.distinctBy { it.id }
        }
    }
}
