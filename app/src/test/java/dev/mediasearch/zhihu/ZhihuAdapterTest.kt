package dev.mediasearch.zhihu

import dev.mediasearch.core.FailureKind
import dev.mediasearch.core.HttpResponse
import dev.mediasearch.core.HttpTransport
import dev.mediasearch.core.PlatformException
import java.security.MessageDigest
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import org.json.JSONObject

class ZhihuAdapterTest {

    @Test
    fun searchUsesReferenceQueryAndExtractsOnlySupportedContent() = runSuspend {
        var requestedUrl = ""
        val transport = object : HttpTransport {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse {
                requestedUrl = url
                assertEquals("d_c0=d; z_c0=z", headers["cookie"])
                assertEquals("zst", headers["x-zst-81"])
                assertEquals("zse", headers["x-zse-96"])
                assertEquals("3.0.91", headers["x-api-version"])
                assertEquals("101_3_3.0", headers["x-zse-93"])
                assertEquals("OS=Web", headers["x-app-za"])
                return HttpResponse(
                    status = 200,
                    body = """
                        {
                          "data": [
                            {"type":"search_result","object":{
                              "type":"answer","id":"42","title":"Q title",
                              "question":{"id":"7","title":"Question"},
                              "excerpt":"<p>Hello &amp; world</p>",
                              "author":{"name":"Alice"},"voteup_count":8
                            }},
                            {"type":"search_result","object":{
                              "type":"article","id":"99","title":"Article",
                              "excerpt":"Summary","author":{"name":"Bob"}
                            }},
                            {"type":"search_result","object":{"type":"unknown","id":"x","title":"skip"}}
                          ],
                          "paging":{"is_end":false}
                        }
                    """.trimIndent(),
                )
            }
        }
        val adapter = ZhihuAdapter(transport, { "d_c0=d; z_c0=z" }) { expression ->
            when {
                expression.startsWith("get_sign_input(") -> JSONObject.quote("input")
                expression == "get_zst_81()" -> JSONObject.quote("zst")
                expression.startsWith("finish(") -> JSONObject.quote("zse")
                else -> error("unexpected expression: $expression")
            }
        }

        val page = adapter.search("中文 query", 2)
        assertTrue(requestedUrl.startsWith("https://www.zhihu.com/api/v4/search_v3?"))
        assertTrue(requestedUrl.contains("gk_version=gz-gaokao&t=general&q=%E4%B8%AD%E6%96%87+query"))
        assertTrue(requestedUrl.contains("offset=20&limit=20"))
        assertEquals(2, page.items.size)
        assertEquals("answer:42", page.items[0].id)
        assertEquals("Q title", page.items[0].title)
        assertEquals("Hello & world", page.items[0].summary)
        assertEquals("https://www.zhihu.com/question/7/answer/42", page.items[0].url)
        assertEquals("Alice", page.items[0].author)
        assertTrue(page.hasMore)
    }

    @Test
    fun verifySessionRequiresUidAndName() = runSuspend {
        val adapter = adapterFor(HttpResponse(200, "{\"uid\":\"u1\",\"name\":\"N\"}"))
        assertTrue(adapter.verifySession())
    }

    @Test
    fun malformedCurrentUserShapeIsParseFailure() {
        val adapter = adapterFor(HttpResponse(200, "{\"uid\":\"u1\"}"))
        val error = assertFailsWith<PlatformException> { runSuspend { adapter.verifySession() } }
        assertEquals(FailureKind.PARSE, error.kind)
    }

    @Test
    fun htmlChallengeIsNotAcceptedAsEmptyJson() {
        val adapter = adapterFor(HttpResponse(200, "<html>captcha challenge</html>"))
        val error = assertFailsWith<PlatformException> {
            runSuspend { adapter.search("query") }
        }
        assertEquals(FailureKind.CHALLENGE, error.kind)
    }

    @Test
    fun missingCookieIsLoginFailure() {
        val adapter = ZhihuAdapter(
            transport = emptyTransport(),
            cookieProvider = { "d_c0=only" },
            evaluate = { error("must not sign without both cookies") },
        )
        val error = assertFailsWith<PlatformException> { runSuspend { adapter.verifySession() } }
        assertEquals(FailureKind.LOGIN_REQUIRED, error.kind)
    }

    @Test
    fun networkFailureIsNotReportedAsLoginFailure() {
        val adapter = ZhihuAdapter(
            transport = object : HttpTransport {
                override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
                    error("offline")
            },
            cookieProvider = { "d_c0=d; z_c0=z" },
            evaluate = { expression ->
                when {
                    expression.startsWith("get_sign_input(") -> JSONObject.quote("input")
                    expression == "get_zst_81()" -> JSONObject.quote("zst")
                    else -> JSONObject.quote("zse")
                }
            },
        )
        val error = assertFailsWith<PlatformException> { runSuspend { adapter.verifySession() } }
        assertEquals(FailureKind.NETWORK, error.kind)
    }

    @Test
    fun signatureUsesUtf8Md5BetweenJavascriptCalls() {
        var finishExpression = ""
        val adapter = ZhihuAdapter(
            transport = object : HttpTransport {
                override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
                    HttpResponse(200, "{\"uid\":\"u\",\"name\":\"n\"}")
            },
            cookieProvider = { "d_c0=d; z_c0=z" },
            evaluate = { expression ->
                when {
                    expression.startsWith("get_sign_input(") -> JSONObject.quote("中文")
                    expression == "get_zst_81()" -> JSONObject.quote("zst")
                    expression.startsWith("finish(") -> {
                        finishExpression = expression
                        JSONObject.quote("zse")
                    }
                    else -> error("unexpected expression")
                }
            },
        )
        runSuspend { adapter.verifySession() }
        val expected = md5("中文")
        assertTrue(finishExpression.contains(JSONObject.quote(expected)))
    }

    private fun adapterFor(response: HttpResponse): ZhihuAdapter = ZhihuAdapter(
        transport = object : HttpTransport {
            override suspend fun get(url: String, headers: Map<String, String>): HttpResponse = response
        },
        cookieProvider = { "d_c0=d; z_c0=z" },
        evaluate = { expression ->
            when {
                expression.startsWith("get_sign_input(") -> JSONObject.quote("input")
                expression == "get_zst_81()" -> JSONObject.quote("zst")
                else -> JSONObject.quote("zse")
            }
        },
    )

    private fun emptyTransport(): HttpTransport = object : HttpTransport {
        override suspend fun get(url: String, headers: Map<String, String>): HttpResponse =
            error("unexpected network call")
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun <T> runSuspend(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) {
                result = value
            }
        })
        return result!!.getOrThrow()
    }
}
