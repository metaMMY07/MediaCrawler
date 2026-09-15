package dev.mediasearch.bilibili

import kotlin.test.Test
import kotlin.test.assertEquals

class BilibiliWbiTest {
    @Test
    fun `wbi signing follows fixed timestamp vector and encodes values`() {
        val keys = BilibiliWbiKeys(
            imgKey = "0123456789abcdef0123456789abcdef",
            subKey = "fedcba9876543210fedcba9876543210"
        )
        assertEquals("1022a87ffdaf532cb45ee953dce8c96d", keys.mixinKey())

        val signed = BilibiliWbi.sign(
            parameters = mapOf(
                "keyword" to "C++ & 空",
                "page" to 1,
                "search_type" to "video"
            ),
            timestampSeconds = 1_700_000_000L,
            mixinKey = keys.mixinKey()
        )
        assertEquals(
            "keyword=C%2B%2B+%26+%E7%A9%BA&page=1&search_type=video" +
                "&wts=1700000000&w_rid=d29e2e68b8dfab107155e6ef53721697",
            signed
        )
    }

    @Test
    fun `wbi signing removes the five forbidden value characters`() {
        val signed = BilibiliWbi.sign(
            parameters = mapOf("value" to "a!'()*b"),
            timestampSeconds = 7L,
            mixinKey = "salt"
        )
        assertEquals("value=ab&wts=7&w_rid=34d68335e83ca3f9379679b702f6b6ea", signed)
    }
}
