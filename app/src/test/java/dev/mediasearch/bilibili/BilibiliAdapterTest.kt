package dev.mediasearch.bilibili

import dev.mediasearch.core.FailureKind
import dev.mediasearch.core.HttpResponse
import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.PlatformException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BilibiliAdapterTest {
    @Test
    fun `session verification requires server login state`() = runTest {
        val authenticated = QueueTransport(HttpResponse(200, """{"code":0,"data":{"isLogin":true}}"""))
        assertEquals(true, BilibiliAdapter(authenticated).verifySession())
        assertEquals(1, authenticated.urls.size)
        assertTrue(authenticated.urls.single().endsWith("/x/web-interface/nav"))
    }

    @Test
    fun `anonymous WBI keys do not count as authenticated account`() = runTest {
        val anonymous = QueueTransport(navResponse(code = -101))
        assertEquals(false, BilibiliAdapter(anonymous).verifySession())
    }

    @Test
    fun `search parses html fields metrics links and pagination`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "<html>home</html>"),
            navResponse(),
            searchResponse(
                page = 1,
                pages = 3,
                result = """
                    [{"bvid":"BV1TEST","aid":123,"title":"<em class=\"keyword\">Kotlin</em> &amp; Android",
                      "author":"<em class=\"keyword\">阿明</em>","description":"<p>摘要&nbsp;内容</p>",
                      "arcurl":"//www.bilibili.com/video/BV1TEST","pic":"//i0.hdslb.com/a.jpg","play":42}]
                """
            )
        )

        val page = BilibiliAdapter(transport).search("Kotlin", page = 1)
        val item = page.items.single()
        assertEquals("BV1TEST", item.id)
        assertEquals("Kotlin & Android", item.title)
        assertEquals("阿明", item.author)
        assertEquals("摘要 内容", item.summary)
        assertEquals("https://www.bilibili.com/video/BV1TEST", item.url)
        assertEquals("https://i0.hdslb.com/a.jpg", item.thumbnailUrl)
        assertEquals("42次播放", item.metric)
        assertTrue(page.hasMore)
        assertEquals(1, page.page)
        assertTrue(transport.urls.any { it.startsWith("https://api.bilibili.com/x/web-interface/wbi/search/type?") })
        assertTrue(transport.headers.all { "User-Agent" !in it })
    }

    @Test
    fun `page count controls hasMore and is reflected in returned page`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"), navResponse(), searchResponse(page = 2, pages = 2, result = "[]")
        )
        val page = BilibiliAdapter(transport).search("x", page = 2)
        assertEquals(false, page.hasMore)
        assertEquals(2, page.page)
    }

    @Test
    fun `homepage and nav keys are cached for subsequent pages`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"), navResponse(),
            searchResponse(page = 1, pages = 2, result = "[]"),
            searchResponse(page = 2, pages = 2, result = "[]")
        )
        val adapter = BilibiliAdapter(transport)
        adapter.search("x", page = 1)
        adapter.search("x", page = 2)
        assertEquals(1, transport.urls.count { it == "https://www.bilibili.com/" })
        assertEquals(1, transport.urls.count { it.endsWith("/x/web-interface/nav") })
    }

    @Test
    fun `anonymous nav may return login code when WBI keys are valid`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"),
            navResponse(code = -101),
            searchResponse(page = 1, pages = 1, result = "[]")
        )
        val page = BilibiliAdapter(transport).search("x")
        assertEquals(emptyList(), page.items)
    }

    @Test
    fun `signature failure refreshes nav once then succeeds`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"),
            navResponse("a".repeat(32), "b".repeat(32)),
            HttpResponse(200, "{\"code\":-403,\"message\":\"非法访问\"}"),
            navResponse("c".repeat(32), "d".repeat(32)),
            searchResponse(page = 1, pages = 1, result = "[]")
        )
        val page = BilibiliAdapter(transport).search("x")
        assertEquals(emptyList(), page.items)
        assertEquals(2, transport.urls.count { it.endsWith("/x/web-interface/nav") })
    }

    @Test
    fun `second signature failure is surfaced`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"), navResponse(),
            HttpResponse(200, "{\"code\":-403}"), navResponse(),
            HttpResponse(200, "{\"code\":-403}")
        )
        val error = assertFailsWith<PlatformException> { BilibiliAdapter(transport).search("x") }
        assertEquals(FailureKind.SIGNATURE, error.kind)
    }

    @Test
    fun `http and malformed business responses are not empty success`() = runTest {
        val transport = QueueTransport(HttpResponse(200, "home"), navResponse(), HttpResponse(429, "{}"))
        val rate = assertFailsWith<PlatformException> { BilibiliAdapter(transport).search("x") }
        assertEquals(FailureKind.RATE_LIMITED, rate.kind)

        val malformed = QueueTransport(HttpResponse(200, "home"), navResponse(), HttpResponse(200, "{}"))
        val parse = assertFailsWith<PlatformException> { BilibiliAdapter(malformed).search("x") }
        assertEquals(FailureKind.PARSE, parse.kind)
    }

    @Test
    fun `missing title is parse failure while an explicit empty result is valid`() = runTest {
        val malformedItem = QueueTransport(
            HttpResponse(200, "home"), navResponse(),
            searchResponse(page = 1, pages = 1, result = "[{\"bvid\":\"BV1\"}]")
        )
        val parse = assertFailsWith<PlatformException> { BilibiliAdapter(malformedItem).search("x") }
        assertEquals(FailureKind.PARSE, parse.kind)

        val empty = QueueTransport(
            HttpResponse(200, "home"), navResponse(), searchResponse(page = 1, pages = 1, result = "[]")
        )
        assertEquals(emptyList(), BilibiliAdapter(empty).search("x").items)
    }

    @Test
    fun `non-empty search voucher is a challenge without retrying`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"),
            navResponse(),
            HttpResponse(200, """{"code":0,"data":{"v_voucher":"challenge-token"}}""")
        )

        val error = assertFailsWith<PlatformException> { BilibiliAdapter(transport).search("x") }

        assertEquals(FailureKind.CHALLENGE, error.kind)
        assertEquals("B站触发验证，请稍后重试", error.message)
        assertEquals(1, transport.urls.count { it.endsWith("/x/web-interface/nav") })
        assertEquals(1, transport.urls.count { it.contains("/x/web-interface/wbi/search/type?") })
        assertEquals(3, transport.urls.size)
    }

    @Test
    fun `empty search voucher does not mask a normal result`() = runTest {
        val transport = QueueTransport(
            HttpResponse(200, "home"),
            navResponse(),
            HttpResponse(
                200,
                """{"code":0,"data":{"v_voucher":"","numPages":1,"result":[{"bvid":"BV1","title":"标题"}]}}"""
            )
        )

        val page = BilibiliAdapter(transport).search("x")

        assertEquals(listOf("BV1"), page.items.map { it.id })
    }

    @Test
    fun `business codes preserve login challenge and rate limit meanings`() = runTest {
        val login = QueueTransport(
            HttpResponse(200, "home"), navResponse(), HttpResponse(200, "{\"code\":-101}")
        )
        val loginError = assertFailsWith<PlatformException> { BilibiliAdapter(login).search("x") }
        assertEquals(FailureKind.LOGIN_REQUIRED, loginError.kind)
        assertEquals("B站需要登录", loginError.message)

        val challenge = QueueTransport(
            HttpResponse(200, "home"), navResponse(), HttpResponse(200, "{\"code\":-352}")
        )
        val challengeError = assertFailsWith<PlatformException> { BilibiliAdapter(challenge).search("x") }
        assertEquals(FailureKind.CHALLENGE, challengeError.kind)
        assertEquals("B站触发验证，请稍后重试", challengeError.message)

        val rate = QueueTransport(
            HttpResponse(200, "home"), navResponse(), HttpResponse(200, "{\"code\":-412}")
        )
        val rateError = assertFailsWith<PlatformException> { BilibiliAdapter(rate).search("x") }
        assertEquals(FailureKind.RATE_LIMITED, rateError.kind)
        assertEquals("B站请求频繁，请稍后重试", rateError.message)
    }

    private fun navResponse(
        img: String = "0123456789abcdef0123456789abcdef",
        sub: String = "fedcba9876543210fedcba9876543210",
        code: Int = 0
    ) =
        HttpResponse(
            200,
            """{"code":$code,"data":{"wbi_img":{"img_url":"https://i0.hdslb.com/bfs/wbi/$img.png","sub_url":"https://i0.hdslb.com/bfs/wbi/$sub.png"}}}"""
        )

    private fun searchResponse(page: Int, pages: Int, result: String) = HttpResponse(
        200,
        """{"code":0,"data":{"page":$page,"numPages":$pages,"result":$result}}"""
    )

    private class QueueTransport(private vararg val responses: HttpResponse) : HttpTransport {
        val urls = mutableListOf<String>()
        val headers = mutableListOf<Map<String, String>>()
        private var index = 0

        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
            urls += url
            this.headers += headers
            return responses.getOrNull(index++) ?: error("Unexpected request: $url")
        }
    }
}
