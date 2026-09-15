package dev.mediasearch

import android.app.Activity
import android.app.Instrumentation
import android.os.Bundle
import android.os.SystemClock
import dev.mediasearch.bilibili.BilibiliAdapter
import dev.mediasearch.network.CronetTransport
import dev.mediasearch.session.LocalJsRuntime
import dev.mediasearch.session.SessionStore
import java.security.MessageDigest
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Development-only runner: offline script equivalence and three opt-in live Bilibili requests. */
class RuntimeProbe : Instrumentation() {
    private var live = false
    override fun onCreate(arguments: Bundle?) { live = arguments?.getString("live") == "true"; start() }
    override fun onStart() {
        val report = JSONObject()
        runBlocking {
            val js = LocalJsRuntime(targetContext)
            var transport: CronetTransport? = null
            try {
                val vectors = JSONArray(context.assets.open("zhihu-vectors.json").bufferedReader().use { it.readText() })
                val init = SystemClock.elapsedRealtime()
                js.evaluate("Math.random = function(){return 0.5;}; 'ready'")
                report.put("js_init_ms", SystemClock.elapsedRealtime() - init)
                val times = JSONArray()
                repeat(vectors.length()) { index ->
                    val vector = vectors.getJSONObject(index)
                    val start = SystemClock.elapsedRealtime()
                    val raw = js.evaluate("get_sign_input(${JSONObject.quote(vector.getString("route"))},${JSONObject.quote(vector.getString("cookie"))})")
                    val input = JSONTokener(raw).nextValue() as String
                    val md5 = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
                    val actual = JSONTokener(js.evaluate("finish(${JSONObject.quote(md5)})")).nextValue() as String
                    check(actual == vector.getJSONObject("expected").getString("x-zse-96")) { "Signature vector $index mismatch" }
                    times.put(SystemClock.elapsedRealtime() - start)
                }
                report.put("signature_vectors_passed", vectors.length()).put("signature_call_ms", times)
                if (live) {
                    val client = CronetTransport(targetContext, SessionStore())
                    transport = client
                    val responses = JSONArray()
                    report.put("response_shapes", responses)
                    val adapter = BilibiliAdapter(object : dev.mediasearch.core.HttpTransport {
                        override suspend fun get(url: String, headers: Map<String, String>): dev.mediasearch.core.HttpResponse {
                            val response = client.get(url, headers)
                            val shape = JSONObject().put("path", java.net.URI(url).path).put("http", response.status)
                            if (url.contains("api.bilibili.com")) {
                                val body = runCatching { JSONObject(response.body) }.getOrNull()
                                shape.put("code", body?.opt("code"))
                                shape.put("data_keys", JSONArray(body?.optJSONObject("data")?.keys()?.asSequence()?.toList() ?: emptyList<String>()))
                            }
                            responses.put(shape)
                            return response
                        }
                    })
                    val searches = JSONArray()
                    repeat(3) { index ->
                        if (index > 0) delay(3000)
                        val start = SystemClock.elapsedRealtime()
                        try {
                            val result = withTimeout(20_000) { adapter.search("Android", 1) }
                            searches.put(JSONObject().put("sample", index + 1).put("elapsed_ms", SystemClock.elapsedRealtime() - start).put("count", result.items.size).put("has_more", result.hasMore).put("success", true))
                        } catch (e: Exception) {
                            searches.put(JSONObject().put("sample", index + 1).put("elapsed_ms", SystemClock.elapsedRealtime() - start).put("success", false).put("error_type", e.javaClass.simpleName).put("message", e.message?.take(120)).put("cause_type", e.cause?.javaClass?.simpleName).put("cause_frames", e.cause?.stackTrace?.take(4)?.joinToString(" | ")))
                            // Do not keep requesting if a platform challenges or refuses the first sample.
                            report.put("live_bilibili", searches)
                            throw e
                        }
                    }
                    report.put("live_bilibili", searches)
                }
                report.put("probe_completed", true)
            } catch (e: Exception) {
                report.put("probe_completed", false).put("error_type", e.javaClass.simpleName)
            } finally {
                withContext(Dispatchers.Main) { js.close() }
                transport?.close()
            }
        }
        val result = Bundle().apply { putString("report", report.toString()) }
        finish(if (report.optBoolean("probe_completed")) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
    }
}
