package so.torii.backend.spring

import com.nimbusds.jose.jwk.JWKSet
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockFilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import so.torii.backend.Auth
import so.torii.backend.TestJwksServer
import so.torii.backend.TestKeys
import so.torii.backend.TokenBuilder
import so.torii.backend.clearJwksCacheForTests

class ToriiAuthenticationFilterTest {
    private lateinit var key: com.nimbusds.jose.jwk.ECKey
    private lateinit var server: TestJwksServer
    private lateinit var issuer: String

    @BeforeEach
    fun setUp() {
        clearJwksCacheForTests()
        SecurityContextHolder.clearContext()
        key = TestKeys.generate()
        server = TestJwksServer(JWKSet(key))
        server.start()
        issuer = server.issuer()
    }

    @AfterEach
    fun tearDown() {
        server.stop()
        SecurityContextHolder.clearContext()
        clearJwksCacheForTests()
    }

    @Test
    fun `populates SecurityContext on valid token`() {
        val token = TokenBuilder(key = key, issuer = issuer, subject = "u-1", pid = "env-1").sign()
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer $token")
        }
        val response = MockHttpServletResponse()
        val chain: FilterChain = MockFilterChain()
        val filter = ToriiAuthenticationFilter(issuer = issuer)

        filter.doFilter(request, response, chain)

        val authentication = SecurityContextHolder.getContext().authentication
        assertNotNull(authentication)
        assertTrue(authentication!!.isAuthenticated)
        val principal = authentication.principal as Auth
        assertEquals("u-1", principal.userId)
        assertEquals("env-1", principal.environmentId)
        assertEquals(200, response.status)
    }

    @Test
    fun `returns 401 when header is missing`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val filter = ToriiAuthenticationFilter(issuer = issuer)

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
        assertTrue(response.contentAsString.contains("unauthorized"))
        assertNull(SecurityContextHolder.getContext().authentication)
    }

    @Test
    fun `returns 401 when scheme is not Bearer`() {
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Basic dXNlcjpwYXNz")
        }
        val response = MockHttpServletResponse()
        val filter = ToriiAuthenticationFilter(issuer = issuer)

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
    }

    @Test
    fun `passes through when optional and header missing`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        val chain = MockFilterChain()
        val filter = ToriiAuthenticationFilter(issuer = issuer, optional = true)

        filter.doFilter(request, response, chain)

        assertEquals(200, response.status)
        assertNull(SecurityContextHolder.getContext().authentication)
        assertSame(request, chain.request)
    }

    @Test
    fun `returns 401 when token is signed by wrong key`() {
        val wrongKey = TestKeys.generate("wrong")
        val token = TokenBuilder(key = key, issuer = issuer, signingKey = wrongKey).sign()
        val request = MockHttpServletRequest().apply {
            addHeader("Authorization", "Bearer $token")
        }
        val response = MockHttpServletResponse()
        val filter = ToriiAuthenticationFilter(issuer = issuer)

        filter.doFilter(request, response, MockFilterChain())

        assertEquals(401, response.status)
        assertNull(SecurityContextHolder.getContext().authentication)
    }
}
