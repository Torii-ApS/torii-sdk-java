package so.torii.backend

import com.nimbusds.jose.jwk.JWKSet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class VerifyTokenTest {
    private lateinit var key: com.nimbusds.jose.jwk.ECKey
    private lateinit var server: TestJwksServer
    private lateinit var issuer: String

    @BeforeEach
    fun setUp() {
        clearJwksCacheForTests()
        key = TestKeys.generate()
        server = TestJwksServer(JWKSet(key))
        server.start()
        issuer = server.issuer()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        clearJwksCacheForTests()
    }

    @Test
    fun `verifies happy-path token and extracts claims`() {
        val token = TokenBuilder(
            key = key,
            issuer = issuer,
            subject = "user-123",
            pid = "env-456",
            emailVerified = true,
            impersonating = false,
            locale = "en",
        ).sign()

        val auth = verifyToken(token, issuer)

        assertEquals("user-123", auth.userId)
        assertEquals("env-456", auth.environmentId)
        assertEquals(issuer, auth.issuer)
        assertTrue(auth.emailVerified)
        assertFalse(auth.impersonating)
        assertEquals("en", auth.locale)
        // Defaults to true when absent.
        assertTrue(auth.profileComplete)
        assertNotNull(auth.raw["sub"])
    }

    @Test
    fun `profile_complete defaults to true when absent and false when explicitly false`() {
        val absent = verifyToken(
            TokenBuilder(key = key, issuer = issuer).sign(),
            issuer,
        )
        assertTrue(absent.profileComplete)

        val explicit = verifyToken(
            TokenBuilder(key = key, issuer = issuer, profileComplete = false).sign(),
            issuer,
        )
        assertFalse(explicit.profileComplete)
    }

    @Test
    fun `locale is null when claim absent`() {
        val auth = verifyToken(TokenBuilder(key = key, issuer = issuer).sign(), issuer)
        assertNull(auth.locale)
    }

    @Test
    fun `rejects token signed by a different key`() {
        val otherKey = TestKeys.generate("other-key")
        val token = TokenBuilder(key = key, issuer = issuer, signingKey = otherKey).sign()
        val ex = assertThrows(ToriiAuthException::class.java) {
            verifyToken(token, issuer)
        }
        assertTrue(ex.message!!.contains("verification failed", ignoreCase = true))
    }

    @Test
    fun `rejects token with wrong issuer`() {
        val token = TokenBuilder(key = key, issuer = issuer).sign()
        // verifyToken expects `https://wrong.example.com` but JWT has `issuer`
        val ex = assertThrows(ToriiAuthException::class.java) {
            verifyToken(token, "https://wrong.example.com")
        }
        // Will fail at JWKS fetch (different issuer URL) — error message
        // mentions verification failure either way.
        assertTrue(ex.message!!.contains("verification failed", ignoreCase = true) ||
            ex.message!!.contains("connect", ignoreCase = true))
    }

    @Test
    fun `rejects token missing pid claim`() {
        val token = TokenBuilder(key = key, issuer = issuer, pid = null).sign()
        val ex = assertThrows(ToriiAuthException::class.java) {
            verifyToken(token, issuer)
        }
        assertTrue(ex.message!!.contains("pid"))
    }

    @Test
    fun `rejects expired token`() {
        val token = TokenBuilder(
            key = key,
            issuer = issuer,
            issuedAt = Instant.now().minusSeconds(600),
            expiresAt = Instant.now().minusSeconds(120),
        ).sign()
        val ex = assertThrows(ToriiAuthException::class.java) {
            verifyToken(token, issuer, leewaySeconds = 0)
        }
        assertTrue(ex.message!!.contains("Expired", ignoreCase = true) ||
            ex.message!!.contains("verification", ignoreCase = true))
    }

    @Test
    fun `rejects blank token`() {
        assertThrows(ToriiAuthException::class.java) { verifyToken("", issuer) }
    }

    @Test
    fun `caches JWKS across calls`() {
        val token1 = TokenBuilder(key = key, issuer = issuer).sign()
        val token2 = TokenBuilder(key = key, issuer = issuer, subject = "user-2").sign()
        verifyToken(token1, issuer)
        verifyToken(token2, issuer)
        // Exactly one JWKS fetch — nimbus' caching JWKSource keeps the keys.
        assertEquals(1, server.requestCount)
    }
}
