package dev.mediasearch.core

enum class Platform(val label: String, val homeUrl: String, val cookieUrl: String) {
    BILIBILI("哔哩哔哩", "https://www.bilibili.com", "https://api.bilibili.com"),
    ZHIHU("知乎", "https://www.zhihu.com/signin", "https://www.zhihu.com"),
    XHS("小红书", "https://www.xiaohongshu.com", "https://edith.xiaohongshu.com")
}

data class SearchItem(
    val id: String,
    val platform: Platform,
    val title: String,
    val author: String,
    val summary: String,
    val url: String,
    val thumbnailUrl: String = "",
    val metric: String = ""
)

data class SearchPage(val items: List<SearchItem>, val hasMore: Boolean, val page: Int)

data class HttpResponse(val status: Int, val body: String, val headers: Map<String, List<String>> = emptyMap())

interface HttpTransport {
    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): HttpResponse
}

enum class FailureKind { LOGIN_REQUIRED, CHALLENGE, RATE_LIMITED, NETWORK, SIGNATURE, PARSE, UNSUPPORTED }

class PlatformException(val kind: FailureKind, override val message: String, cause: Throwable? = null) : Exception(message, cause)

interface SearchAdapter {
    suspend fun search(query: String, page: Int = 1): SearchPage
}
