package dev.mediasearch.session

import android.webkit.CookieManager
import dev.mediasearch.core.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

enum class SessionStatus(val label: String) {
    GUEST("未登录"), MISSING("未登录"), CAPTURED("会话已保存，待确认"), VERIFIED("已登录"), EXPIRED("需要重新登录")
}

class SessionStore(context: android.content.Context? = null) {
    private val preferences = context?.getSharedPreferences("account_confirmation", android.content.Context.MODE_PRIVATE)
    private val fingerprints = mutableMapOf<Platform, String>()
    private val _statuses = MutableStateFlow(Platform.entries.associateWith {
        if (it == Platform.BILIBILI) SessionStatus.GUEST else SessionStatus.MISSING
    })
    val statuses = _statuses.asStateFlow()

    suspend fun cookies(url: String): String = withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(url).orEmpty()
    }

    suspend fun scan(platform: Platform): Boolean {
        val fingerprint = SessionEvidence.fingerprint(platform, cookies(platform.cookieUrl) + ";" + cookies(platform.homeUrl))
        val previous = fingerprints[platform]
        if (fingerprint == null) {
            fingerprints.remove(platform)
            mark(platform, SessionStatus.MISSING)
            return false
        }
        fingerprints[platform] = fingerprint
        val confirmed = preferences?.getString(platform.name, null) == fingerprint ||
            (previous == fingerprint && _statuses.value[platform] == SessionStatus.VERIFIED)
        _statuses.value = _statuses.value + (platform to if (confirmed) SessionStatus.VERIFIED else SessionStatus.CAPTURED)
        return true
    }

    fun mark(platform: Platform, status: SessionStatus) {
        _statuses.value = _statuses.value + (platform to status)
        if (status == SessionStatus.VERIFIED) {
            fingerprints[platform]?.let { preferences?.edit()?.putString(platform.name, it)?.apply() }
        } else if (status == SessionStatus.MISSING || status == SessionStatus.EXPIRED) {
            preferences?.edit()?.remove(platform.name)?.apply()
        }
    }

    suspend fun accept(url: String, headers: Map<String, List<String>>) = withContext(Dispatchers.Main) {
        headers.entries.filter { it.key.equals("set-cookie", true) }.flatMap { it.value }.forEach { value ->
            suspendCancellableCoroutine<Unit> { continuation ->
                CookieManager.getInstance().setCookie(url, value) {
                    if (continuation.isActive) continuation.resume(Unit)
                }
            }
        }
    }

    suspend fun persist() = withContext(Dispatchers.IO) { CookieManager.getInstance().flush() }
}
