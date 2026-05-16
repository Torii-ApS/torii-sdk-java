package so.torii.backend.spring

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import so.torii.backend.Auth
import so.torii.backend.ToriiAuthException
import so.torii.backend.verifyToken

/**
 * Spring Security filter that turns an incoming `Authorization: Bearer ...`
 * torii JWT into an [Authentication] whose principal is a [Auth].
 *
 * Wire it into a `SecurityFilterChain` *before*
 * `UsernamePasswordAuthenticationFilter`:
 *
 * ```kotlin
 * @Bean
 * fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
 *     val filter = ToriiAuthenticationFilter(issuer = "https://acme.torii.so")
 *     http
 *         .csrf { it.disable() }
 *         .authorizeHttpRequests { it.anyRequest().authenticated() }
 *         .sessionManagement { it.sessionCreationPolicy(STATELESS) }
 *         .addFilterBefore(filter, UsernamePasswordAuthenticationFilter::class.java)
 *     return http.build()
 * }
 * ```
 *
 * If [optional] is true, requests without an `Authorization` header pass
 * through unauthenticated (downstream filters / authorization rules then
 * decide). If false (default), the filter writes a 401 response and stops
 * the chain.
 *
 * @property issuer Expected issuer URL — strict equality with the JWT's `iss`.
 * @property audience Optional `aud` claim to enforce.
 * @property leewaySeconds Clock-skew tolerance (default 30 seconds).
 * @property optional Whether unauthenticated requests should pass through.
 */
public class ToriiAuthenticationFilter
@JvmOverloads
constructor(
    private val issuer: String,
    private val audience: String? = null,
    private val leewaySeconds: Long = 30,
    private val optional: Boolean = false,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val header = request.getHeader("Authorization")
        if (header.isNullOrBlank()) {
            if (optional) {
                filterChain.doFilter(request, response)
            } else {
                writeUnauthorized(response, "Missing Authorization header")
            }
            return
        }
        val match = BEARER_REGEX.matchEntire(header)
        if (match == null) {
            writeUnauthorized(response, "Authorization header is not in 'Bearer <token>' form")
            return
        }
        val token = match.groupValues[1].trim()
        val auth: Auth = try {
            verifyToken(token, issuer, audience, leewaySeconds)
        } catch (e: ToriiAuthException) {
            writeUnauthorized(response, e.message ?: "Invalid token")
            return
        }

        val authentication = ToriiAuthenticationToken(auth)
        SecurityContextHolder.getContext().authentication = authentication
        filterChain.doFilter(request, response)
    }

    private fun writeUnauthorized(response: HttpServletResponse, message: String) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.setHeader("WWW-Authenticate", """Bearer error="invalid_token"""")
        response.contentType = "application/json"
        response.writer.write("""{"error":"unauthorized","detail":${quote(message)}}""")
    }

    private fun quote(s: String): String {
        val escaped = s.replace("\\", "\\\\").replace("\"", "\\\"")
        return "\"$escaped\""
    }

    public companion object {
        private val BEARER_REGEX = Regex("""^Bearer\s+(.+)$""", RegexOption.IGNORE_CASE)
    }
}

/**
 * Spring Security [Authentication] whose principal is the verified torii
 * [Auth]. Always reports `isAuthenticated = true` once constructed —
 * unverified tokens are rejected upstream in the filter.
 */
public class ToriiAuthenticationToken(
    private val auth: Auth,
) : AbstractAuthenticationToken(emptyList()) {

    init {
        isAuthenticated = true
    }

    override fun getCredentials(): Any = ""
    override fun getPrincipal(): Auth = auth
    override fun getName(): String = auth.userId
}
