package dev.mediasearch.session

import android.webkit.WebView
import dev.mediasearch.core.BrowserProfile
import dev.mediasearch.core.Platform
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Read an official page's authentication result, never form values or credentials. */
object PageSessionProbe {
    suspend fun authenticated(view: WebView, platform: Platform): Boolean? {
        if (!BrowserProfile.allowed(platform, view.url.orEmpty())) return null
        val script = when (platform) {
            Platform.XHS -> """(() => {
                const state = window.__INITIAL_STATE__?.user?.loggedIn;
                const value = state?.value ?? state;
                return typeof value === 'boolean' ? value : null;
            })()"""
            else -> {
                val route = if (platform == Platform.BILIBILI) "https://api.bilibili.com/x/web-interface/nav" else "https://www.zhihu.com/api/v4/me"
                val condition = if (platform == Platform.BILIBILI) "j?.data?.isLogin" else "(j?.id ? true : null)"
                """(() => {
                    if (!window.__openScopeAuthAt || Date.now() - window.__openScopeAuthAt > 5000) {
                        window.__openScopeAuthAt = Date.now();
                        fetch('$route', {credentials:'include'}).then(async r => {
                            if (r.status === 401) {window.__openScopeAuth = false; return;}
                            if (!r.ok) return;
                            const j = await r.json(); const v = $condition;
                            if (typeof v === 'boolean') window.__openScopeAuth = v;
                        }).catch(() => {});
                    }
                    return window.__openScopeAuth ?? null;
                })()"""
            }
        }
        return when (evaluatePage(view, script)) { "true" -> true; "false" -> false; else -> null }
    }
}

suspend fun evaluatePage(view: WebView, script: String): String = suspendCancellableCoroutine { c ->
    view.evaluateJavascript(script) { result -> if (c.isActive) c.resume(result ?: "null") }
}
