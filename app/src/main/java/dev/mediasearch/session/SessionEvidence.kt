package dev.mediasearch.session

import dev.mediasearch.core.Platform
import java.security.MessageDigest

/** Locally remember a previously confirmed credential identity; never infer login from cookie names. */
object SessionEvidence {
    fun fingerprint(platform: Platform, cookies: String): String? {
        val values = cookies.split(';').mapNotNull {
            val pair = it.trim().split('=', limit = 2)
            if (pair.size == 2 && pair[1].isNotBlank()) pair[0] to pair[1] else null
        }.groupBy({ it.first }, { it.second })
        val names = when (platform) {
            Platform.BILIBILI -> listOf("SESSDATA")
            Platform.ZHIHU -> listOf("d_c0", "z_c0")
            Platform.XHS -> listOf("web_session")
        }
        if (!names.all { it in values }) return null
        val identity = names.joinToString(";") { "$it=${values.getValue(it).distinct().sorted().joinToString(",")}" }
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
