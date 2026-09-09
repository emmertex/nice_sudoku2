import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RequestBodyTest {
    @Test fun exactLimitAcceptedAndNextByteRejected() = runBlocking {
        assertEquals("a".repeat(65536), readLimitedBody(ByteReadChannel("a".repeat(65536))))
        assertFailsWith<RequestBodyTooLarge> { readLimitedBody(ByteReadChannel(" ".repeat(65537))) }
        assertFailsWith<RequestBodyTooLarge> { readLimitedBody(ByteReadChannel("é".repeat(32769))) }
        assertEquals("ok", readLimitedBody(ByteReadChannel(" ok ")))
    }

    @Test fun forwardedAddressesRequireTrustedImmediatePeer() {
        val trusted = setOf("172.18.0.2", "192.0.2.10")
        assertEquals("203.0.113.5", resolveClientIp("203.0.113.5", "fake", "fake", trusted))
        assertEquals("203.0.113.7", resolveClientIp("172.18.0.2", "203.0.113.7", null, trusted))
        assertEquals("203.0.113.8", resolveClientIp("172.18.0.2", null, "spoofed, 203.0.113.8, 192.0.2.10", trusted))
    }
}
