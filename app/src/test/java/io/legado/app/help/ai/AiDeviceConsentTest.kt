package io.legado.app.help.ai

import org.junit.Assert.*
import org.junit.Test

class AiDeviceConsentTest {
    private class Store : AiDeviceConsentStore {
        var value: String? = null
        override fun read() = value
        override fun write(normalizedServiceUrl: String) { value = normalizedServiceUrl }
        override fun clear() { value = null }
    }

    @Test fun consentFollowsCanonicalEndpointButNotAnotherServiceOrRestore() {
        val store = Store()
        val consent = AiDeviceConsent(store)
        assertFalse(consent.isConfirmed("https://api.example/v1"))
        consent.confirm("https://API.EXAMPLE:443/v1/")
        assertTrue(consent.isConfirmed("https://api.example/v1/chat/completions"))
        assertFalse(consent.isConfirmed("https://other.example/v1"))
        assertFalse(consent.isConfirmed("https://api.example/tenant/v1"))
        assertFalse(AiDeviceConsent(Store()).isConfirmed("https://api.example/v1"))
        consent.clear()
        assertFalse(consent.isConfirmed("https://api.example/v1"))
    }

    @Test fun unsafeOrCredentialBearingAddressesCannotBeConfirmed() {
        for (url in listOf("", "ftp://example.org", "http://example.org", "https://name:secret@example.org/v1",
            "https://example.org/v1?token=secret", "https://example.org:70000/v1")) {
            val store = Store()
            assertTrue(runCatching { AiDeviceConsent(store).confirm(url) }.isFailure)
            assertNull(store.value)
        }
    }
}
