package dev.mediasearch.core

import java.net.URI

/** Desktop-site preference shared by visible pages and their native API requests. */
object BrowserProfile {
    fun desktop(platform: Platform) = platform == Platform.BILIBILI || platform == Platform.XHS

    fun desktopUserAgent(default: String): String = default
        .replaceFirst(Regex("\\([^)]*\\)"), "(X11; Linux x86_64)")
        .replace(Regex("\\sVersion/\\S+"), "")
        .replace(" Mobile", "")

    fun userAgent(platform: Platform, default: String): String =
        if (desktop(platform)) desktopUserAgent(default) else default

    /** Some official redirects downgrade to HTTP. Reissue as HTTPS without sending cleartext. */
    fun secureNavigationUrl(platform: Platform, url: String): String? {
        val secure = if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url
        return secure.takeIf { allowed(platform, it) }?.let { pageUrl(platform, it) }
    }

    fun allowed(platform: Platform, url: String): Boolean = runCatching {
        val uri = URI(url)
        val root = when (platform) {
            Platform.BILIBILI -> "bilibili.com"
            Platform.ZHIHU -> "zhihu.com"
            Platform.XHS -> "xiaohongshu.com"
        }
        val host = uri.host?.lowercase() ?: return false
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            (host == root || host.endsWith(".$root"))
    }.getOrDefault(false)

    fun pageUrl(platform: Platform, url: String): String {
        if (!allowed(platform, url)) return url
        val uri = URI(url)
        return when {
            platform == Platform.BILIBILI && uri.host in setOf("m.bilibili.com", "bilibili.com") ->
                "https://www.bilibili.com" + uri.rawPath.orEmpty().ifEmpty { "/" } +
                    uri.rawQuery?.let { "?$it" }.orEmpty() + uri.rawFragment?.let { "#$it" }.orEmpty()
            platform == Platform.XHS && uri.path in setOf("", "/") -> "https://www.xiaohongshu.com/explore"
            else -> url
        }
    }
}
