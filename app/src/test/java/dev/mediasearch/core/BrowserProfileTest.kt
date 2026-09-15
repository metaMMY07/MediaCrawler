package dev.mediasearch.core

import kotlin.test.*

class BrowserProfileTest {
    @org.junit.Test fun officialDowngradeIsUpgradedWithoutDroppingQuery() {
        org.junit.Assert.assertEquals("https://www.xiaohongshu.com/search_result/?keyword=Android&source=web_explore_feed",
            BrowserProfile.secureNavigationUrl(Platform.XHS, "http://www.xiaohongshu.com/search_result/?keyword=Android&source=web_explore_feed"))
        org.junit.Assert.assertNull(BrowserProfile.secureNavigationUrl(Platform.XHS, "http://xiaohongshu.com.evil.test/search_result"))
        org.junit.Assert.assertNull(BrowserProfile.secureNavigationUrl(Platform.XHS, "http://www.xiaohongshu.com:80/search_result"))
    }
    private val mobile = "Mozilla/5.0 (Linux; Android 15; Pixel 7 Build/AP3A; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/124.0.6367.219 Mobile Safari/537.36"

    @Test fun `desktop mode retains actual engine version and removes mobile markers`() {
        val ua = BrowserProfile.userAgent(Platform.BILIBILI, mobile)
        assertTrue("Chrome/124.0.6367.219" in ua)
        assertTrue("(X11; Linux x86_64)" in ua)
        assertTrue("(KHTML, like Gecko)" in ua)
        assertFalse("Mobile" in ua)
        assertFalse("Android" in ua)
        assertFalse("Version/4.0" in ua)
        assertEquals(mobile, BrowserProfile.userAgent(Platform.ZHIHU, mobile))
    }

    @Test fun `bili content normalization preserves encoded query and fragment`() {
        assertEquals("https://www.bilibili.com/video/BV123?p=2&keyword=%E7%8C%AB#reply",
            BrowserProfile.pageUrl(Platform.BILIBILI, "https://m.bilibili.com/video/BV123?p=2&keyword=%E7%8C%AB#reply"))
        val passport = "https://passport.bilibili.com/login"
        assertEquals(passport, BrowserProfile.pageUrl(Platform.BILIBILI, passport))
    }

    @Test fun `xhs explore is entrypoint but signed note URLs are unchanged`() {
        assertEquals("https://www.xiaohongshu.com/explore", BrowserProfile.pageUrl(Platform.XHS, "https://www.xiaohongshu.com"))
        val note = "https://www.xiaohongshu.com/explore/123?xsec_token=fixture%2Bvalue&xsec_source=pc_search"
        assertEquals(note, BrowserProfile.pageUrl(Platform.XHS, note))
    }

    @Test fun `lookalikes insecure schemes and userinfo never become trusted desktop pages`() {
        listOf("https://www.bilibili.com.evil.example/", "https://www.bilibili.com@evil.example/",
            "http://www.bilibili.com/", "https://www.bilibili.com:8443/", "javascript:alert(1)").forEach {
            assertFalse(BrowserProfile.allowed(Platform.BILIBILI, it))
            assertEquals(it, BrowserProfile.pageUrl(Platform.BILIBILI, it))
        }
        assertTrue(BrowserProfile.allowed(Platform.BILIBILI, "https://passport.bilibili.com/login"))
        assertFalse(BrowserProfile.allowed(Platform.XHS, "https://www.bilibili.com/"))
    }
}
