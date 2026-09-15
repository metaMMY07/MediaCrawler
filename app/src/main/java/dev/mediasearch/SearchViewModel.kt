package dev.mediasearch

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.mediasearch.bilibili.BilibiliAdapter
import dev.mediasearch.core.*
import dev.mediasearch.network.CronetTransport
import dev.mediasearch.session.*
import dev.mediasearch.zhihu.ZhihuAdapter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class PlatformResult(
    val loading: Boolean = false,
    val items: List<SearchItem> = emptyList(),
    val message: String? = null,
    val failure: FailureKind? = null,
    val elapsedMs: Long? = null,
    val cached: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 0
)

data class SearchState(
    val searchId: Int = 0,
    val input: String = "",
    val query: String = "",
    val selected: Platform? = null,
    val results: Map<Platform, PlatformResult> = emptyMap()
)

class SearchViewModel(application: Application) : AndroidViewModel(application) {
    val sessions = SessionStore(application)
    private val transportDelegate = lazy { CronetTransport(application, sessions) }
    private val transport by transportDelegate
    private val js = LocalJsRuntime(application)
    private val bili by lazy { BilibiliAdapter(transport) }
    private val zhihu by lazy { ZhihuAdapter(transport, { sessions.cookies(Platform.ZHIHU.cookieUrl) }, js::evaluate) }
    private val xhs = dev.mediasearch.xhs.XhsPageClient(application, sessions)
    private val xhsAccount = dev.mediasearch.xhs.XhsPageClient(application, sessions)
    private val _state = MutableStateFlow(SearchState())
    val state = _state.asStateFlow()
    private var queryJob: Job? = null
    private val pages = mutableMapOf<Platform, Job>()
    private var generation = 0
    private data class CacheKey(val platform: Platform, val query: String)
    private data class Cached(val time: Long, val data: SearchPage)
    private val cache = LinkedHashMap<CacheKey, Cached>()
    private val lastRequestAt = mutableMapOf<Platform, Long>()
    private val cooldownUntil = mutableMapOf<Platform, Long>()

    fun refreshAccounts() {
        viewModelScope.launch {
            Platform.entries.forEach { platform ->
                launch {
                    try { verifyLogin(platform) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Keep captured session; a network failure is not a logout. */ }
                }
            }
        }
    }

    fun persistSessions() { viewModelScope.launch { sessions.persist() } }

    fun input(value: String) { _state.update { it.copy(input = value.take(120)) } }
    fun select(platform: Platform?) { _state.update { it.copy(selected = platform) } }

    fun search() {
        val query = _state.value.input.trim()
        if (query.isBlank()) return
        generation++
        val token = generation
        queryJob?.cancel()
        pages.values.forEach { it.cancel() }
        pages.clear()
        val selected = _state.value.selected?.let { listOf(it) } ?: Platform.entries
        _state.update { it.copy(searchId = token, query = query, results = selected.associateWith { PlatformResult(loading = true) }) }
        queryJob = viewModelScope.launch {
            supervisorScope { selected.forEach { platform -> launch { load(platform, query, 1, token) } } }
        }
    }

    fun retry(platform: Platform) {
        val query = _state.value.query
        if (query.isBlank() || _state.value.results[platform]?.loading == true) return
        cache.remove(CacheKey(platform, query))
        pages[platform]?.cancel()
        val token = generation
        setResult(platform, PlatformResult(loading = true), token)
        pages[platform] = viewModelScope.launch { load(platform, query, 1, token) }
    }

    fun more(platform: Platform) {
        val current = _state.value.results[platform] ?: return
        if (current.loading || !current.hasMore) return
        val query = _state.value.query
        val token = generation
        setResult(platform, current.copy(loading = true), token)
        pages[platform] = viewModelScope.launch { load(platform, query, current.page + 1, token) }
    }

    private suspend fun load(platform: Platform, query: String, page: Int, token: Int) {
        val old = if (page > 1) (_state.value.results[platform] ?: PlatformResult()).copy(loading = false) else PlatformResult()
        setResult(platform, old.copy(loading = true, message = null, failure = null), token)
        val start = SystemClock.elapsedRealtime()
        try {
            if (platform == Platform.ZHIHU && !sessions.scan(platform)) {
                throw PlatformException(FailureKind.LOGIN_REQUIRED, "登录知乎后，即可在这里搜索问答与文章")
            }
            val key = CacheKey(platform, query)
            val cached = if (page == 1 && platform == Platform.BILIBILI) cache[key]?.takeIf { start - it.time < 90_000 } else null
            val remaining = (cooldownUntil[platform] ?: 0) - SystemClock.elapsedRealtime()
            if (cached == null && remaining > 0) {
                setResult(platform, old.copy(message = "平台请求已暂停，请约 ${(remaining + 999) / 1000} 秒后重试，或查看官方网页", failure = FailureKind.RATE_LIMITED), token)
                return
            }
            val result = cached?.data ?: withTimeout(18_000) {
                val now = SystemClock.elapsedRealtime()
                // User-triggered requests only; no polling or automatic pagination.
                val interval = 3_000 - (now - (lastRequestAt[platform] ?: 0))
                if (interval > 0) delay(interval)
                lastRequestAt[platform] = SystemClock.elapsedRealtime()
                when (platform) {
                    Platform.BILIBILI -> bili.search(query, page)
                    Platform.ZHIHU -> zhihu.search(query, page)
                    Platform.XHS -> xhs.search(query, page)
                    else -> error("Unavailable adapter")
                }
            }
            val elapsed = SystemClock.elapsedRealtime() - start
            if (page == 1 && platform == Platform.BILIBILI && cached == null) {
                if (cache.size >= 30) cache.remove(cache.keys.first())
                cache[key] = Cached(SystemClock.elapsedRealtime(), result)
            }
            setResult(platform, PlatformResult(
                items = (old.items + result.items).distinctBy { it.id },
                elapsedMs = elapsed, cached = cached != null, hasMore = result.hasMore, page = result.page
            ), token)
            Log.i("MediaSearchMetrics", "search platform=${platform.name} page=$page elapsed_ms=$elapsed cache=${cached != null} count=${result.items.size}")
        } catch (e: TimeoutCancellationException) {
            setResult(platform, old.copy(message = "请求超时，其他平台不受影响", failure = FailureKind.NETWORK), token)
        } catch (e: CancellationException) { throw e
        } catch (e: PlatformException) {
            if (e.kind == FailureKind.LOGIN_REQUIRED) sessions.mark(platform, SessionStatus.EXPIRED)
            if (e.kind == FailureKind.CHALLENGE || e.kind == FailureKind.RATE_LIMITED) {
                cooldownUntil[platform] = SystemClock.elapsedRealtime() + 60_000
            }
            setResult(platform, old.copy(message = e.message, failure = e.kind), token)
            Log.i("MediaSearchMetrics", "search platform=${platform.name} failure=${e.kind.name}")
        } catch (e: Exception) {
            setResult(platform, old.copy(message = "暂时无法读取结果，请稍后重试", failure = FailureKind.PARSE), token)
            Log.i("MediaSearchMetrics", "search platform=${platform.name} failure=UNEXPECTED type=${e.javaClass.simpleName}")
            if (BuildConfig.DEBUG) Log.d("MediaSearchMetrics", e.stackTrace.filter { it.className.startsWith("dev.mediasearch") }.take(5).joinToString(" | "))
        }
    }

    private fun setResult(platform: Platform, result: PlatformResult, token: Int) {
        if (token == generation) _state.update { it.copy(results = it.results + (platform to result)) }
    }

    suspend fun verifyLogin(platform: Platform): Boolean {
        if (!sessions.scan(platform)) return false
        if (platform == Platform.XHS) {
            if (sessions.statuses.value[platform] == SessionStatus.VERIFIED) return true
            val result = xhsAccount.verifySession()
            if (result == true) { sessions.mark(platform, SessionStatus.VERIFIED); sessions.persist() }
            else if (result == false) sessions.mark(platform, SessionStatus.MISSING)
            return result == true
        }
        val verified = withTimeout(18_000) {
            if (platform == Platform.BILIBILI) bili.verifySession() else zhihu.verifySession()
        }
        if (verified) {
            sessions.mark(platform, SessionStatus.VERIFIED)
            sessions.persist()
        }
        else sessions.mark(platform, SessionStatus.EXPIRED)
        return verified
    }

    override fun onCleared() {
        queryJob?.cancel()
        pages.values.forEach { it.cancel() }
        js.close()
        xhs.close()
        xhsAccount.close()
        if (transportDelegate.isInitialized()) transport.close()
        super.onCleared()
    }
}
