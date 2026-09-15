package dev.mediasearch.zhihu

// Copyright (c) 2025 relakkes@gmail.com
//
// This file is part of the MediaCrawler-derived Zhihu adapter.
// Repository: https://github.com/NanmiCoder/MediaCrawler
// Licensed under NON-COMMERCIAL LEARNING LICENSE 1.1.
//
// This adapter is supplied for learning and research only.  It must not be
// used for commercial purposes, large-scale crawling, or to disrupt Zhihu.

import dev.mediasearch.core.FailureKind
import dev.mediasearch.core.HttpResponse
import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.Platform
import dev.mediasearch.core.PlatformException
import dev.mediasearch.core.SearchAdapter
import dev.mediasearch.core.SearchItem
import dev.mediasearch.core.SearchPage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/**
 * A small, cookie-backed Zhihu search adapter.
 *
 * [evaluate] is deliberately injected by the Android host.  The host loads
 * assets/signing/zhihu.js once in its WebView and evaluates the expressions
 * supplied here.  WebView.evaluateJavascript returns JSON-encoded values;
 * [decodeJavascriptString] therefore decodes the result before it is used.
 */
public class ZhihuAdapter(
    private val transport: HttpTransport,
    private val cookieProvider: suspend () -> String,
    private val evaluate: suspend (String) -> String,
) : SearchAdapter {

    public override suspend fun search(query: String, page: Int): SearchPage {
        if (query.isBlank()) {
            throw PlatformException(FailureKind.PARSE, "知乎搜索词不能为空")
        }
        if (page < 1) {
            throw PlatformException(FailureKind.PARSE, "知乎页码无效")
        }

        val pageSize = 20
        val offset = (page - 1) * pageSize
        val params = linkedMapOf(
            "gk_version" to "gz-gaokao",
            "t" to "general",
            "q" to query,
            "correction" to "1",
            "offset" to offset.toString(),
            "limit" to pageSize.toString(),
            "filter_fields" to "",
            "lc_idx" to offset.toString(),
            "show_all_topics" to "0",
            "search_source" to "Filter",
            "time_interval" to "",
            "sort" to "",
            "vertical" to "",
        )
        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${urlEncode(key)}=${urlEncode(value)}"
        }
        val path = "/api/v4/search_v3?$queryString"
        val root = getJson(path)
        val data = root.optJSONArray("data")
            ?: throw PlatformException(FailureKind.PARSE, "知乎搜索响应缺少 data")
        val items = extractItems(data)
        val paging = root.optJSONObject("paging")
        val hasMore = paging?.let { !it.optBoolean("is_end", true) } ?: false
        return SearchPage(items = items, hasMore = hasMore, page = page)
    }

    /**
     * Checks the current cookie-backed session with the signed /api/v4/me
     * endpoint.  Blank uid/name fields or an explicit unauthenticated marker
     * are login failures.  Other malformed field shapes are parse failures;
     * transport, challenge, and signature errors keep their own kinds.
     */
    public suspend fun verifySession(): Boolean {
        val root = getJson("/api/v4/me?include=email%2Cis_active%2Cis_bind_phone")
        val hasUid = root.has("uid")
        val hasName = root.has("name")
        if (!hasUid && !hasName) {
            if (isExplicitlyUnauthenticated(root)) {
                throw PlatformException(FailureKind.LOGIN_REQUIRED, "知乎会话未登录或已失效")
            }
            throw PlatformException(FailureKind.PARSE, "知乎当前用户响应缺少 uid/name")
        }
        if (!hasUid || !hasName) {
            throw PlatformException(FailureKind.PARSE, "知乎当前用户响应字段异常")
        }
        val uid = root.optString("uid", "").trim()
        val name = root.optString("name", "").trim()
        if (uid.isEmpty() || name.isEmpty()) {
            throw PlatformException(FailureKind.LOGIN_REQUIRED, "知乎会话未登录或已失效")
        }
        return true
    }

    private suspend fun getJson(path: String): JSONObject {
        val cookie = currentCookie()
        val headers = sign(path, cookie) + mapOf(
            "accept" to "*/*",
            "accept-language" to "zh-CN,zh;q=0.9",
            "cookie" to cookie,
            "priority" to "u=1, i",
            "referer" to REFERER,
            "x-api-version" to "3.0.91",
            "x-app-za" to "OS=Web",
            "x-requested-with" to "fetch",
            "x-zse-93" to "101_3_3.0",
        )
        val response = try {
            transport.get(BASE_URL + path, headers)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: PlatformException) {
            throw known
        } catch (_: Exception) {
            // Never turn a transport problem into LOGIN_REQUIRED.
            throw PlatformException(FailureKind.NETWORK, "知乎网络请求失败")
        }
        checkHttpStatus(response)
        return parseBusinessResponse(response)
    }

    private suspend fun sign(path: String, cookie: String): Map<String, String> {
        val md5 = try {
            val inputExpression = "get_sign_input(${JSONObject.quote(path)},${JSONObject.quote(cookie)})"
            val plain = decodeJavascriptString(evaluate(inputExpression))
            md5Hex(plain)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: PlatformException) {
            throw known
        } catch (_: Exception) {
            throw PlatformException(FailureKind.SIGNATURE, "知乎请求签名失败")
        }

        try {
            val zst81 = decodeJavascriptString(evaluate("get_zst_81()"))
            val zse96 = decodeJavascriptString(evaluate("finish(${JSONObject.quote(md5)})"))
            if (zst81.isBlank() || zse96.isBlank()) {
                throw PlatformException(FailureKind.SIGNATURE, "知乎请求签名为空")
            }
            return mapOf(
                "x-zst-81" to zst81,
                "x-zse-96" to zse96,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: PlatformException) {
            throw known
        } catch (_: Exception) {
            throw PlatformException(FailureKind.SIGNATURE, "知乎请求签名失败")
        }
    }

    private suspend fun currentCookie(): String {
        val cookie = try {
            cookieProvider()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (known: PlatformException) {
            throw known
        } catch (_: Exception) {
            throw PlatformException(FailureKind.NETWORK, "知乎会话读取失败")
        }
        if (!hasCookie(cookie, "d_c0") || !hasCookie(cookie, "z_c0")) {
            throw PlatformException(FailureKind.LOGIN_REQUIRED, "知乎会话缺少必要 cookie")
        }
        return cookie
    }

    private fun checkHttpStatus(response: HttpResponse) {
        when (response.status) {
            in 200..299 -> Unit
            401 -> throw PlatformException(FailureKind.LOGIN_REQUIRED, "知乎会话未登录或已失效")
            403 -> throw PlatformException(FailureKind.CHALLENGE, "知乎接口请求被拒绝，可能需要完成验证")
            429 -> throw PlatformException(FailureKind.RATE_LIMITED, "知乎请求过于频繁")
            else -> throw PlatformException(FailureKind.NETWORK, "知乎接口返回 HTTP ${response.status}")
        }
    }

    private fun parseBusinessResponse(response: HttpResponse): JSONObject {
        val body = response.body.trim()
        if (body.startsWith("<") && CHALLENGE_MARKERS.containsMatchIn(body)) {
            throw PlatformException(FailureKind.CHALLENGE, "知乎接口要求完成验证")
        }
        val root = try {
            JSONObject(response.body)
        } catch (_: Exception) {
            throw PlatformException(FailureKind.PARSE, "知乎响应不是有效 JSON")
        }

        val error = root.optJSONObject("error")
        val errorValue = if (root.has("error") && !root.isNull("error")) root.opt("error") else null
        val hasBusinessError = error != null || (errorValue != null && errorValue != false)
        if (hasBusinessError) {
            val message = error?.optString("message", "") ?: root.optString("message", "")
            val lowered = message.lowercase(Locale.ROOT)
            val code = error?.optInt("code", root.optInt("code", 0)) ?: root.optInt("code", 0)
            when {
                code == 403 || listOf("captcha", "challenge", "验证", "风控").any { lowered.contains(it) } ->
                    throw PlatformException(FailureKind.CHALLENGE, "知乎接口要求完成验证")
                code == 429 -> throw PlatformException(FailureKind.RATE_LIMITED, "知乎请求过于频繁")
                code == 401 || listOf("login", "登录", "未登录", "unauthorized", "401").any { lowered.contains(it) } ->
                    throw PlatformException(FailureKind.LOGIN_REQUIRED, "知乎会话未登录或已失效")
                else -> throw PlatformException(FailureKind.PARSE, "知乎接口返回业务错误")
            }
        }
        return root
    }

    private fun extractItems(data: JSONArray): List<SearchItem> {
        val result = ArrayList<SearchItem>(data.length())
        val seen = HashSet<String>()
        var supportedCount = 0
        for (index in 0 until data.length()) {
            val entry = data.optJSONObject(index) ?: continue
            val objectValue = entry.optJSONObject("object") ?: entry
            val type = objectValue.optString("type", entry.optString("type", ""))
                .lowercase(Locale.ROOT)
            val normalizedType = when (type) {
                "search_result" -> objectValue.optString("object_type", "").lowercase(Locale.ROOT)
                else -> type
            }
            if (normalizedType !in SUPPORTED_TYPES) continue
            supportedCount++

            val id = firstString(objectValue, "id", "answer_id", "article_id", "question_id")
            if (id.isEmpty()) continue
            val question = objectValue.optJSONObject("question")
            val title = cleanText(
                firstString(objectValue, "title", "name")
                    .ifEmpty { firstString(question, "title", "name") },
            )
            if (title.isEmpty()) continue

            val url = contentUrl(normalizedType, id, objectValue, question)
            if (url.isEmpty()) continue
            val author = extractAuthor(objectValue)
            val summary = cleanText(
                firstString(objectValue, "excerpt", "description", "content")
                    .ifEmpty { firstString(question, "excerpt", "description") },
            )
            val thumbnail = firstString(objectValue, "thumbnail", "thumbnail_url", "image_url", "image")
            val metric = firstString(
                objectValue,
                "voteup_count",
                "follower_count",
                "comment_count",
                "answer_count",
            )
            val key = "$normalizedType:$id"
            if (!seen.add(key)) continue
            result += SearchItem(
                id = key,
                platform = Platform.ZHIHU,
                title = title,
                author = author,
                summary = summary,
                url = url,
                thumbnailUrl = thumbnail,
                metric = metric,
            )
        }
        if (supportedCount > 0 && result.isEmpty()) {
            throw PlatformException(FailureKind.PARSE, "知乎搜索结果无法解析")
        }
        return result
    }

    private fun isExplicitlyUnauthenticated(root: JSONObject): Boolean {
        for (field in listOf("is_login", "is_authenticated", "logged_in")) {
            if (!root.has(field) || root.isNull(field)) continue
            when (val value = root.opt(field)) {
                is Boolean -> if (!value) return true
                is Number -> if (value.toInt() == 0) return true
                is String -> if (value.lowercase(Locale.ROOT) in setOf("false", "0", "anonymous", "未登录")) return true
            }
        }
        return false
    }

    private fun contentUrl(type: String, id: String, item: JSONObject, question: JSONObject?): String {
        val explicit = firstString(item, "url", "link")
        return when (type) {
            "answer" -> {
                val questionId = firstString(question, "id", "question_id")
                if (questionId.isNotEmpty()) "$BASE_URL/question/$questionId/answer/$id"
                else webUrl(explicit).ifEmpty { "$BASE_URL/answer/$id" }
            }
            "article" -> "$ARTICLE_BASE_URL/p/$id"
            "question" -> "$BASE_URL/question/$id"
            "zvideo", "video" -> webUrl(explicit).ifEmpty { "$BASE_URL/zvideo/$id" }
            else -> webUrl(explicit)
        }
    }

    private fun extractAuthor(item: JSONObject): String {
        val author = item.optJSONObject("author") ?: item.optJSONObject("creator")
        if (author != null) {
            val direct = firstString(author, "name", "nickname", "url_token")
            if (direct.isNotEmpty()) return cleanText(direct)
            val member = author.optJSONObject("member")
            if (member != null) return cleanText(firstString(member, "name", "nickname"))
        }
        return cleanText(firstString(item, "author_name", "creator_name"))
    }

    private fun webUrl(value: String): String {
        return if (value.startsWith("https://www.zhihu.com/") ||
            value.startsWith("https://zhuanlan.zhihu.com/")) value else ""
    }

    private fun firstString(objectValue: JSONObject?, vararg names: String): String {
        if (objectValue == null) return ""
        for (name in names) {
            if (!objectValue.has(name) || objectValue.isNull(name)) continue
            val value = objectValue.opt(name)
            if (value is String) {
                if (value.isNotBlank()) return value
            } else if (value != null && value != JSONObject.NULL) {
                val text = value.toString()
                if (text.isNotBlank()) return text
            }
        }
        return ""
    }

    private fun cleanText(value: String): String {
        if (value.isEmpty()) return ""
        return value
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("[ \\t]+"), " ")
            .trim()
    }

    private fun decodeJavascriptString(raw: String): String {
        val value = raw.trim()
        if (value.isEmpty() || value == "null" || value == "undefined") {
            throw PlatformException(FailureKind.SIGNATURE, "知乎签名脚本返回空值")
        }
        if (value.startsWith('"')) {
            val decoded = JSONTokener(value).nextValue()
            if (decoded !is String) {
                throw PlatformException(FailureKind.SIGNATURE, "知乎签名脚本返回值无效")
            }
            return decoded
        }
        // This fallback keeps JVM fakes convenient while still accepting the
        // real WebView contract above.
        return value
    }

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun hasCookie(cookie: String, name: String): Boolean {
        val pattern = Regex("(?:^|;)\\s*${Regex.escape(name)}=([^;]*)")
        return pattern.find(cookie)?.groupValues?.getOrNull(1)?.isNotBlank() == true
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private companion object {
        const val BASE_URL = "https://www.zhihu.com"
        const val ARTICLE_BASE_URL = "https://zhuanlan.zhihu.com"
        const val REFERER = "https://www.zhihu.com/search?q=python&time_interval=a_year&type=content"
        val SUPPORTED_TYPES = setOf("answer", "article", "question", "zvideo", "video")
        val CHALLENGE_MARKERS = Regex("captcha|challenge|验证|安全验证|风控", RegexOption.IGNORE_CASE)
    }
}
