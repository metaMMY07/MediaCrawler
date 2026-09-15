package dev.mediasearch.xhs

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XhsPageClientTest {
    private fun row(url: String) = JSONObject().put("url", url).put("title", "测试笔记").put("author", "测试作者")
    @Test fun keepsOriginalSecurityQueryAndDeduplicates() {
        val url = "https://www.xiaohongshu.com/search_result/0123456789abcdef01234567?xsec_token=synthetic%2Btest&xsec_source=pc_search"
        val rows = JSONObject().put("items", JSONArray().put(row(url)).put(row(url)))
        val items = XhsPageClient.parse(rows)
        assertEquals(1, items.size)
        assertEquals(url, items.single().url)
        assertTrue(XhsPageClient.parse(rows, setOf(items.single().id)).isEmpty())
    }
    @Test fun rejectsExternalLinksAndNonNotePages() {
        val rows = JSONObject().put("items", JSONArray()
            .put(row("https://www.xiaohongshu.com.evil.test/explore/0123456789abcdef01234567"))
            .put(row("https://www.xiaohongshu.com/explore"))
            .put(row("javascript:alert(1)")))
        assertTrue(XhsPageClient.parse(rows).isEmpty())
    }
}
