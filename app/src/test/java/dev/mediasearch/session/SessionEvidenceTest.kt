package dev.mediasearch.session

import dev.mediasearch.core.Platform
import org.junit.Assert.*
import org.junit.Test

class SessionEvidenceTest {
    @Test fun missingOrEmptyCredentialsDoNotProduceIdentity() {
        assertNull(SessionEvidence.fingerprint(Platform.BILIBILI, "buvid3=x; SESSDATA="))
        assertNull(SessionEvidence.fingerprint(Platform.ZHIHU, "d_c0=x"))
        assertNull(SessionEvidence.fingerprint(Platform.XHS, "a1=x"))
    }
    @Test fun trackingChangesAndDuplicateDomainCookiesKeepSameIdentity() {
        val first = SessionEvidence.fingerprint(Platform.BILIBILI, "SESSDATA=synthetic-test; buvid3=a")
        assertEquals(first, SessionEvidence.fingerprint(Platform.BILIBILI, "buvid3=b;SESSDATA=synthetic-test;SESSDATA=synthetic-test"))
        assertNotEquals(first, SessionEvidence.fingerprint(Platform.BILIBILI, "SESSDATA=changed-test"))
        assertEquals(64, first!!.length)
        assertFalse(first.contains("synthetic-test"))
    }
}
