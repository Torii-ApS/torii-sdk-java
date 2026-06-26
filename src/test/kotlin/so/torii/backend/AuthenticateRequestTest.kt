package so.torii.backend

import com.nimbusds.jose.jwk.JWKSet
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AuthenticateRequestTest {
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
    fun `reads bearer token and verifies happy path`() {
        val token = TokenBuilder(
            key = key,
            issuer = issuer,
            subject = "u1",
            pid = "env-1",
        ).sign()

        val auth = authenticateRequest(
            headers = mapOf("Authorization" to listOf("Bearer $token")),
            issuer = issuer,
        )

        assertEquals("u1", auth.userId)
        assertEquals("env-1", auth.environmentId)
    }

    @Test
    fun `lowercase authorization header works`() {
        val token = TokenBuilder(key = key, issuer = issuer).sign()
        val auth = authenticateRequest(
            headers = mapOf("authorization" to listOf("Bearer $token")),
            issuer = issuer,
        )
        assertEquals("11111111-1111-1111-1111-111111111111", auth.userId)
    }

    @Test
    fun `throws when authorization header is missing`() {
        val ex = assertThrows(ToriiAuthException::class.java) {
            authenticateRequest(headers = emptyMap(), issuer = issuer)
        }
        assertEquals("Missing Authorization header", ex.message)
    }

    @Test
    fun `throws when header is not in bearer form`() {
        val ex = assertThrows(ToriiAuthException::class.java) {
            authenticateRequest(
                headers = mapOf("Authorization" to listOf("Basic dXNlcjpwYXNz")),
                issuer = issuer,
            )
        }
        assertEquals("Authorization header is not in 'Bearer <token>' form", ex.message)
    }

    @Test
    fun `custom header name is honoured`() {
        val token = TokenBuilder(key = key, issuer = issuer).sign()
        val auth = authenticateRequest(
            headers = mapOf("X-Forwarded-Auth" to listOf("Bearer $token")),
            issuer = issuer,
            header = "X-Forwarded-Auth",
        )
        assertEquals("11111111-1111-1111-1111-111111111111", auth.userId)
    }
}
