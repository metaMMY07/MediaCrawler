package dev.mediasearch.network

import android.content.Context
import android.webkit.WebSettings
import dev.mediasearch.core.*
import dev.mediasearch.session.SessionStore
import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.*
import org.chromium.net.*

/** One embedded engine; no Google Play services, local server or credential logging. */
class CronetTransport(private val context: Context, private val sessions: SessionStore) : HttpTransport {
    private val executor = Executors.newFixedThreadPool(2)
    private val lifecycleLock = Any()
    private val requests = mutableSetOf<UrlRequest>()
    private var closed = false
    private val engineDelegate = lazy {
        CronetEngine.Builder(context.applicationContext)
            .enableHttp2(true).enableQuic(true)
            .setUserAgent(WebSettings.getDefaultUserAgent(context))
            .build()
    }
    private val engine by engineDelegate
    private val defaultUserAgent by lazy { WebSettings.getDefaultUserAgent(context) }

    fun close() = synchronized(lifecycleLock) {
        closed = true
        requests.toList().forEach { it.cancel() }
        finishShutdown()
    }

    private fun completed(request: UrlRequest) = synchronized(lifecycleLock) {
        requests.remove(request)
        finishShutdown()
    }

    private fun finishShutdown() {
        if (closed && requests.isEmpty() && !executor.isShutdown) {
            if (engineDelegate.isInitialized()) engine.shutdown()
            executor.shutdown()
        }
    }

    override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = withTimeout(15_000) {
        var current = url
        repeat(5) {
            requireAllowed(current)
            val requestHeaders = headers.filterKeys { !it.equals("cookie", true) }.toMutableMap()
            val platform = when (URI(current).host) {
                "www.bilibili.com", "m.bilibili.com", "api.bilibili.com" -> Platform.BILIBILI
                "www.xiaohongshu.com", "edith.xiaohongshu.com" -> Platform.XHS
                else -> Platform.ZHIHU
            }
            if (requestHeaders.keys.none { it.equals("user-agent", true) }) {
                requestHeaders["User-Agent"] = BrowserProfile.userAgent(platform, defaultUserAgent)
            }
            // An explicit adapter snapshot is only used for the original request. Never forward it on redirects.
            val cookie = if (current == url) headers.entries.firstOrNull { it.key.equals("cookie", true) }?.value
                ?: sessions.cookies(current) else sessions.cookies(current)
            if (cookie.isNotBlank()) requestHeaders["Cookie"] = cookie
            val response = withContext(Dispatchers.IO) { request(current, requestHeaders) }
            sessions.accept(current, response.headers)
            if (response.status !in setOf(301, 302, 303, 307, 308)) return@withTimeout response
            val location = response.headers.entries.firstOrNull { it.key.equals("location", true) }?.value?.firstOrNull()
                ?: return@withTimeout response
            val next = URI(current).resolve(location).toString()
            requireAllowed(next)
            // Bilibili redirects its homepage to the mobile homepage for the WebView UA.
            // Permit only that bootstrap transition; signed API redirects require visible recovery.
            val from = URI(current)
            val to = URI(next)
            val mobileHomepage = from.host == "www.bilibili.com" && from.path in setOf("", "/") &&
                to.host == "m.bilibili.com" && to.path in setOf("", "/")
            if (to.host != from.host && !mobileHomepage) {
                throw PlatformException(FailureKind.LOGIN_REQUIRED, "请求转向登录页面，请在 App 内重新登录")
            }
            current = next
        }
        throw PlatformException(FailureKind.NETWORK, "重定向次数过多，请稍后重试")
    }

    private fun requireAllowed(url: String) {
        val uri = URI(url)
        require(uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) && uri.host in API_HOSTS) {
            "Unsupported request destination"
        }
    }

    private suspend fun request(url: String, headers: Map<String, String>): HttpResponse = suspendCancellableCoroutine { continuation ->
        val bytes = ByteArrayOutputStream()
        val buffer = ByteBuffer.allocateDirect(32 * 1024)
        val callback = object : UrlRequest.Callback() {
            override fun onRedirectReceived(request: UrlRequest, info: UrlResponseInfo, newLocationUrl: String) {
                if (continuation.isActive) continuation.resume(HttpResponse(info.httpStatusCode, "", info.allHeaders))
                request.cancel()
            }
            override fun onResponseStarted(request: UrlRequest, info: UrlResponseInfo) { request.read(buffer) }
            override fun onReadCompleted(request: UrlRequest, info: UrlResponseInfo, byteBuffer: ByteBuffer) {
                byteBuffer.flip()
                val chunk = ByteArray(byteBuffer.remaining())
                byteBuffer.get(chunk)
                if (bytes.size() + chunk.size > 4 * 1024 * 1024) {
                    if (continuation.isActive) continuation.resumeWithException(PlatformException(FailureKind.PARSE, "响应过大，已停止读取"))
                    request.cancel()
                    return
                }
                bytes.write(chunk)
                byteBuffer.clear()
                request.read(byteBuffer)
            }
            override fun onSucceeded(request: UrlRequest, info: UrlResponseInfo) {
                completed(request)
                if (continuation.isActive) continuation.resume(HttpResponse(info.httpStatusCode, bytes.toString("UTF-8"), info.allHeaders))
            }
            override fun onFailed(request: UrlRequest, info: UrlResponseInfo?, error: CronetException) {
                completed(request)
                if (continuation.isActive) continuation.resumeWithException(PlatformException(FailureKind.NETWORK, "网络连接失败，请检查网络后重试"))
            }
            override fun onCanceled(request: UrlRequest, info: UrlResponseInfo?) { completed(request) }
        }
        try {
            synchronized(lifecycleLock) {
                check(!closed) { "Transport closed" }
                val builder = engine.newUrlRequestBuilder(url, callback, executor).setHttpMethod("GET").disableCache()
                headers.forEach { (name, value) -> builder.addHeader(name, value) }
                val request = builder.build()
                requests.add(request)
                continuation.invokeOnCancellation { request.cancel() }
                if (continuation.isActive) request.start() else completed(request)
            }
        } catch (error: Exception) {
            if (dev.mediasearch.BuildConfig.DEBUG) {
                // Types and frames only: never include exception messages that may contain request data.
                android.util.Log.w("MediaSearchTransport", error.javaClass.name + " " + error.stackTrace.take(5).joinToString(" | "))
            }
            if (continuation.isActive) continuation.resumeWithException(error)
        }
    }

    companion object {
        private val API_HOSTS = setOf("www.bilibili.com", "m.bilibili.com", "api.bilibili.com", "www.zhihu.com", "www.xiaohongshu.com", "edith.xiaohongshu.com")
    }
}
