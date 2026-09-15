package dev.mediasearch.bilibili

import dev.mediasearch.core.FailureKind
import dev.mediasearch.core.HttpResponse
import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.Platform
import dev.mediasearch.core.PlatformException
import dev.mediasearch.core.SearchAdapter
import dev.mediasearch.core.SearchItem
import dev.mediasearch.core.SearchPage
import java.util.Locale
import kotlinx.coroutines.CancellationException
import org.json.JSONException
import org.json.JSONObject

/**
 * Native HTTP adapter for Bilibili video search.
 *
 * The transport owns cookie persistence.  The adapter only performs a single
 * homepage request to let a CookieManager establish the normal `.bilibili.com`
 * session, and never reads or stores cookie values itself.
 */
class BilibiliAdapter(private val transport: HttpTransport) : SearchAdapter {
    private companion object {
        const val HOME_URL = "https://www.bilibili.com/"
        const val API_BASE = "https://api.bilibili.com"
        const val NAV_PATH = "/x/web-interface/nav"
        const val SEARCH_PATH = "/x/web-interface/wbi/search/type"
        const val PAGE_SIZE = 20
        val BROWSER_HEADERS = mapOf(
            "Accept" to "application/json, text/plain, */*",
            "Referer" to "https://www.bilibili.com/"
        )
    }

    @Volatile
    private var cachedWbiKeys: BilibiliWbiKeys? = null

    @Volatile
    private var cookieBootstrapped = false

    suspend fun verifySession(): Boolean = getJson(API_BASE + NAV_PATH)
        .optJSONObject("data")?.optBoolean("isLogin", false) == true

    override suspend fun search(query: String, page: Int): SearchPage {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            throw PlatformException(FailureKind.UNSUPPORTED, "B站搜索关键词不能为空")
        }
        if (page < 1) {
            throw PlatformException(FailureKind.UNSUPPORTED, "B站搜索页码必须大于0")
        }

        bootstrapCookiesIfNeeded()

        var signatureRetried = false
        while (true) {
            val keys = getWbiKeys(forceRefresh = signatureRetried)
            val requestParameters = mapOf(
                "search_type" to "video",
                "keyword" to normalizedQuery,
                "page" to page,
                "page_size" to PAGE_SIZE,
                // Empty order is the reference client's comprehensive sort;
                // the WBI signer still includes and sorts this parameter.
                "order" to "",
                "pubtime_begin_s" to 0,
                "pubtime_end_s" to 0
            )
            val queryString = try {
                BilibiliWbi.sign(
                    parameters = requestParameters,
                    timestampSeconds = System.currentTimeMillis() / 1000L,
                    mixinKey = keys.mixinKey()
                )
            } catch (e: IllegalArgumentException) {
                throw PlatformException(FailureKind.SIGNATURE, "B站签名失败")
            }

            try {
                val root = getJson(API_BASE + SEARCH_PATH + "?" + queryString)
                return parseSearchPage(root, page)
            } catch (e: PlatformException) {
                if (e.kind == FailureKind.SIGNATURE && !signatureRetried) {
                    // A stale nav key is recoverable once.  The second failure
                    // is surfaced as SIGNATURE instead of becoming an empty page.
                    cachedWbiKeys = null
                    signatureRetried = true
                    continue
                }
                throw e
            }
        }
    }

    private suspend fun bootstrapCookiesIfNeeded() {
        if (cookieBootstrapped) return
        val response = request(HOME_URL)
        if (response.status !in 200..399) {
            throw classifyHttpFailure(response.status)
        }
        cookieBootstrapped = true
    }

    private suspend fun getWbiKeys(forceRefresh: Boolean): BilibiliWbiKeys {
        if (!forceRefresh) {
            cachedWbiKeys?.let { return it }
        }

        val root = getJson(API_BASE + NAV_PATH)
        val data = root.optJSONObject("data")
            ?: throw PlatformException(FailureKind.SIGNATURE, "B站密钥响应缺少数据")
        val image = data.optJSONObject("wbi_img")
            ?: throw PlatformException(FailureKind.SIGNATURE, "B站密钥响应无WBI密钥")
        val imgKey = extractKey(image.optString("img_url"))
        val subKey = extractKey(image.optString("sub_url"))
        val keys = BilibiliWbiKeys(imgKey, subKey)
        try {
            keys.mixinKey()
        } catch (_: IllegalArgumentException) {
            throw PlatformException(FailureKind.SIGNATURE, "B站WBI密钥无效")
        }
        cachedWbiKeys = keys
        return keys
    }

    private fun extractKey(url: String): String {
        val filename = url.substringAfterLast('/').substringBeforeLast('.')
        if (filename.isBlank()) {
            throw PlatformException(FailureKind.SIGNATURE, "B站WBI密钥缺失")
        }
        return filename
    }

    private suspend fun getJson(url: String): JSONObject {
        val response = request(url)
        if (response.status !in 200..299) {
            throw classifyHttpFailure(response.status)
        }
        val root = try {
            JSONObject(response.body)
        } catch (_: JSONException) {
            throw PlatformException(FailureKind.PARSE, "B站返回的数据格式错误")
        }
        if (!root.has("code")) {
            throw PlatformException(FailureKind.PARSE, "B站响应缺少业务码")
        }
        val code = root.optLong("code", Long.MIN_VALUE)
        if (code == Long.MIN_VALUE) {
            throw PlatformException(FailureKind.PARSE, "B站业务码格式错误")
        }
        if (code != 0L) {
            // Anonymous nav responses commonly use -101 while still carrying
            // usable WBI keys.  That code remains a hard login failure for
            // search responses; only this nav-key bootstrap path can accept it.
            if (!(url.contains(NAV_PATH) && code == -101L && hasUsableWbiImage(root))) {
                throw businessFailure(code, url.contains(SEARCH_PATH))
            }
        }
        return root
    }

    private fun hasUsableWbiImage(root: JSONObject): Boolean {
        val image = root.optJSONObject("data")?.optJSONObject("wbi_img") ?: return false
        return try {
            BilibiliWbiKeys(
                extractKey(image.optString("img_url")),
                extractKey(image.optString("sub_url"))
            ).mixinKey()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseSearchPage(root: JSONObject, page: Int): SearchPage {
        val data = root.optJSONObject("data")
            ?: throw PlatformException(FailureKind.PARSE, "B站搜索响应缺少数据")
        val voucher = data.opt("v_voucher")
        if (voucher is String && voucher.isNotBlank()) {
            throw PlatformException(FailureKind.CHALLENGE, messageForKind(FailureKind.CHALLENGE))
        }
        val result = data.optJSONArray("result")
            ?: throw PlatformException(FailureKind.PARSE, "B站搜索响应缺少结果列表")

        val items = ArrayList<SearchItem>(result.length())
        for (index in 0 until result.length()) {
            val raw = result.optJSONObject(index)
                ?: throw PlatformException(FailureKind.PARSE, "B站搜索结果项格式错误")
            items += parseSearchItem(raw)
        }

        val totalPages = data.optInt("numPages", 0)
        val hasMore = if (totalPages > 0) {
            page < totalPages
        } else {
            items.size >= PAGE_SIZE
        }
        return SearchPage(items = items, hasMore = hasMore, page = page)
    }

    private fun parseSearchItem(raw: JSONObject): SearchItem {
        val bvid = raw.optString("bvid").trim()
        val aid = raw.optString("aid").trim()
        val id = bvid.ifEmpty { aid }
        if (id.isEmpty()) {
            throw PlatformException(FailureKind.PARSE, "B站搜索结果缺少视频标识")
        }

        val title = cleanHtml(raw.optString("title"), preserveTagSpacing = false)
        if (title.isEmpty()) {
            throw PlatformException(FailureKind.PARSE, "B站搜索结果缺少标题")
        }
        val author = cleanHtml(raw.optString("author"), preserveTagSpacing = false)
        val summary = cleanHtml(
            raw.optString("description").ifEmpty { raw.optString("desc") },
            preserveTagSpacing = true
        )
        val url = canonicalVideoUrl(raw.optString("arcurl"), bvid, aid)
        val thumbnail = raw.optString("pic").trim()
            .let { if (it.startsWith("//")) "https:$it" else it }
        val play = raw.opt("play")
        val metric = formatPlayMetric(play)

        return SearchItem(
            id = id,
            platform = Platform.BILIBILI,
            title = title,
            author = author,
            summary = summary,
            url = url,
            thumbnailUrl = thumbnail,
            metric = metric
        )
    }

    private fun formatPlayMetric(play: Any?): String {
        val value = when (play) {
            null, JSONObject.NULL -> ""
            is Number -> play.toLong().toString()
            else -> play.toString().trim()
        }
        if (value.isEmpty()) return ""
        if (value.endsWith("播放") || value.endsWith("次")) return value
        return "${value}次播放"
    }

    private fun canonicalVideoUrl(rawUrl: String, bvid: String, aid: String): String {
        val trimmed = rawUrl.trim()
        val normalized = when {
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> trimmed
        }
        if (normalized.startsWith("https://www.bilibili.com/video/")) return normalized
        if (bvid.isNotEmpty()) return "https://www.bilibili.com/video/$bvid"
        if (aid.isNotEmpty()) return "https://www.bilibili.com/video/av$aid"
        return ""
    }

    private suspend fun request(url: String): HttpResponse {
        return try {
            transport.get(url, BROWSER_HEADERS)
        } catch (e: CancellationException) {
            throw e
        } catch (e: PlatformException) {
            throw PlatformException(e.kind, messageForKind(e.kind))
        } catch (e: Exception) {
            throw PlatformException(FailureKind.NETWORK, "B站网络请求失败，请稍后重试", e)
        }
    }

    private fun classifyHttpFailure(status: Int): PlatformException {
        val kind = when {
            status == 401 -> FailureKind.LOGIN_REQUIRED
            status == 403 -> FailureKind.CHALLENGE
            status == 412 || status == 429 -> FailureKind.RATE_LIMITED
            status in 500..599 -> FailureKind.NETWORK
            status == 400 -> FailureKind.PARSE
            status !in 200..299 -> FailureKind.NETWORK
            else -> FailureKind.PARSE
        }
        return PlatformException(kind, messageForKind(kind))
    }

    private fun businessFailure(code: Long, isSearch: Boolean): PlatformException {
        val kind = when (code) {
            -101L -> FailureKind.LOGIN_REQUIRED
            -352L -> FailureKind.CHALLENGE
            -412L -> FailureKind.RATE_LIMITED
            -403L -> if (isSearch) FailureKind.SIGNATURE else FailureKind.CHALLENGE
            else -> FailureKind.PARSE
        }
        return PlatformException(kind, messageForKind(kind))
    }

    private fun messageForKind(kind: FailureKind): String {
        return when (kind) {
            FailureKind.LOGIN_REQUIRED -> "B站需要登录"
            FailureKind.CHALLENGE -> "B站触发验证，请稍后重试"
            FailureKind.RATE_LIMITED -> "B站请求频繁，请稍后重试"
            FailureKind.SIGNATURE -> "B站签名失效，请稍后重试"
            FailureKind.PARSE -> "B站接口返回异常"
            FailureKind.NETWORK -> "B站网络请求失败，请稍后重试"
            else -> "B站接口请求失败，请稍后重试"
        }
    }

    private fun cleanHtml(value: String, preserveTagSpacing: Boolean): String {
        if (value.isBlank()) return ""
        val withoutScripts = value.replace(
            Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>"),
            ""
        )
        val tagReplacement = if (preserveTagSpacing) " " else ""
        return decodeEntities(
            withoutScripts.replace(Regex("(?s)<[^>]*>"), tagReplacement)
        ).replace(Regex("[\\s\\u200B\\u200C\\u200D\\uFEFF]+"), " ").trim()
    }

    private fun decodeEntities(value: String): String {
        return value.replace(Regex("&(#x[0-9a-fA-F]+|#\\d+|amp|lt|gt|quot|apos|nbsp);")) { match ->
            when (val entity = match.groupValues[1].lowercase(Locale.ROOT)) {
                "amp" -> "&"
                "lt" -> "<"
                "gt" -> ">"
                "quot" -> "\""
                "apos" -> "'"
                "nbsp" -> " "
                else -> {
                    val number = try {
                        if (entity.startsWith("#x")) {
                            entity.substring(2).toInt(16)
                        } else {
                            entity.substring(1).toInt()
                        }
                    } catch (_: NumberFormatException) {
                        -1
                    }
                    if (number in 0..0x10FFFF) String(Character.toChars(number)) else match.value
                }
            }
        }
    }
}
