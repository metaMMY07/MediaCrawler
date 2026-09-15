package dev.mediasearch.session

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import dev.mediasearch.core.FailureKind
import dev.mediasearch.core.PlatformException
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** App-owned offline script only. No JS-to-native bridge, file access, or remote content. */
class LocalJsRuntime(private val context: Context) {
    private var webView: WebView? = null
    private val lock = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun evaluate(expression: String): String = lock.withLock {
        withContext(Dispatchers.Main) {
            withTimeout(8_000) {
                if (webView == null) {
                    val source = withContext(Dispatchers.IO) {
                        context.assets.open("signing/zhihu.js").bufferedReader().use { it.readText() }
                    }
                    val view = WebView(context)
                    view.settings.javaScriptEnabled = true
                    view.settings.allowFileAccess = false
                    view.settings.allowContentAccess = false
                    view.settings.blockNetworkLoads = true
                    try {
                        suspendCancellableCoroutine<Unit> { continuation ->
                            view.webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView, url: String?) {
                                    if (continuation.isActive) continuation.resume(Unit)
                                }
                            }
                            view.loadDataWithBaseURL("https://signing.invalid/", "<html><body></body></html>", "text/html", "UTF-8", null)
                        }
                        call(view, "$source\n;\"loaded\";")
                        webView = view
                    } catch (e: Exception) { view.destroy(); throw e }
                }
                call(requireNotNull(webView), expression)
            }
        }
    }

    private suspend fun call(view: WebView, script: String): String = suspendCancellableCoroutine { continuation ->
        view.evaluateJavascript(script) { result ->
            if (continuation.isActive) {
                if (result == null || result == "null") continuation.resumeWithException(PlatformException(FailureKind.SIGNATURE, "签名运行失败，请重试"))
                else continuation.resume(result)
            }
        }
    }

    fun close() { webView?.destroy(); webView = null }
}
