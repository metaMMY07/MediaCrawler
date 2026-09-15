package dev.mediasearch.bilibili

import java.security.MessageDigest

/**
 * Small, platform independent implementation of Bilibili's WBI request
 * signing protocol.
 *
 * The algorithm was checked against the fixed MediaCrawler fork snapshot in
 * `.reference/MediaCrawler/media_platform/bilibili/help.py` and the public
 * WBI protocol documentation.  This is a clean Kotlin reimplementation; no
 * unrelated MediaCrawler code is copied.  The referenced fork is distributed
 * under NON-COMMERCIAL LEARNING LICENSE 1.1.
 */
internal object BilibiliWbi {
    private val mixinTable = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
        27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
        37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
        22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
    )

    /** Returns the 32-character mixin key derived from the two nav keys. */
    fun mixinKey(imgKey: String, subKey: String): String {
        val source = imgKey + subKey
        require(source.length >= mixinTable.maxOrNull()!! + 1) {
            "Bilibili WBI keys are too short"
        }
        return buildString(mixinTable.size) {
            mixinTable.forEach { append(source[it]) }
        }.take(32)
    }

    /**
     * Signs parameters and returns the URL query string, including `wts` and
     * `w_rid`. Values use the same application/x-www-form-urlencoded UTF-8
     * encoding as the web endpoint (spaces become `+`).
     */
    fun sign(parameters: Map<String, Any?>, timestampSeconds: Long, mixinKey: String): String {
        require(mixinKey.isNotEmpty()) { "Bilibili WBI mixin key is empty" }
        val sanitized = parameters
            .toMutableMap()
            .apply { put("wts", timestampSeconds) }
            .toSortedMap()
            .mapValues { (_, value) ->
                value.toString().filterNot { it in "!'()*" }
            }

        val canonical = sanitized.entries.joinToString("&") { (key, value) ->
            "${encode(key)}=${encode(value)}"
        }
        val digest = md5Hex(canonical + mixinKey)
        return "$canonical&w_rid=$digest"
    }

    private fun encode(value: String): String {
        // Match Python's urllib.parse.urlencode used by the reference
        // implementation: RFC 3986 unreserved bytes stay literal, spaces
        // become '+', and every other UTF-8 byte is percent encoded.
        val bytes = value.toByteArray(Charsets.UTF_8)
        return buildString(bytes.size) {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                when {
                    unsigned == 0x20 -> append('+')
                    unsigned in 'A'.code..'Z'.code ||
                        unsigned in 'a'.code..'z'.code ||
                        unsigned in '0'.code..'9'.code ||
                        unsigned == '-'.code || unsigned == '_'.code ||
                        unsigned == '.'.code || unsigned == '~'.code ->
                        append(unsigned.toChar())
                    else -> append('%').append(HEX[unsigned ushr 4]).append(HEX[unsigned and 0x0f])
                }
            }
        }
    }

    private const val HEX = "0123456789ABCDEF"

    private fun md5Hex(value: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }
}

internal data class BilibiliWbiKeys(val imgKey: String, val subKey: String) {
    fun mixinKey(): String = BilibiliWbi.mixinKey(imgKey, subKey)
}
