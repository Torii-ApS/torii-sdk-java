@file:JvmName("VerifyToken")

package so.torii.backend

import com.nimbusds.jose.JOSEException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.JWKSourceBuilder
import com.nimbusds.jose.proc.BadJOSEException
import com.nimbusds.jose.proc.JWSVerificationKeySelector
import com.nimbusds.jose.proc.SimpleSecurityContext
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.nimbusds.jwt.proc.BadJWTException
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier
import com.nimbusds.jwt.proc.DefaultJWTProcessor
import java.net.URL
import java.text.ParseException
import java.util.concurrent.ConcurrentHashMap

// Networkless JWT verification. The first call to verifyToken() for a given
// issuer fetches that issuer's JWKS over HTTPS; subsequent calls reuse the
// cached JWKS until the cache TTL expires or kid rotation forces a refetch
// (nimbus-jose-jwt's caching JWKSource handles both).
//
// This is the core DX win behind a backend SDK — `verifyToken(token, issuer)`
// has no per-request round trip to torii.

private const val DEFAULT_LEEWAY_SECONDS: Long = 30
private const val JWKS_PATH = "/_torii/.well-known/jwks.json"

// One processor per issuer so kid rotation + JWKS caching are scoped
// correctly. Keys are normalized (no trailing slash).
private val processors = ConcurrentHashMap<String, DefaultJWTProcessor<SimpleSecurityContext>>()

private fun normalizeIssuer(issuer: String): String =
    issuer.trimEnd('/')

private fun processorForIssuer(issuer: String): DefaultJWTProcessor<SimpleSecurityContext> {
    val normalized = normalizeIssuer(issuer)
    return processors.computeIfAbsent(normalized) { iss ->
        // Hard-coded path: torii's JWKS endpoint lives at /_torii/.well-known/jwks.json
        // for every tenant. We could discover via /.well-known/openid-configuration
        // first, but that's an extra round-trip on the cold path for no gain —
        // the JWKS URL is a stable contract documented in our OIDC discovery doc.
        val retriever = DefaultResourceRetriever(
            /* connectTimeout = */ 2_000,
            /* readTimeout = */ 2_000,
            /* sizeLimit = */ 50 * 1024,
        )
        val jwkSource = JWKSourceBuilder
            .create<SimpleSecurityContext>(URL(iss + JWKS_PATH), retriever)
            .retrying(true)
            .build()
        DefaultJWTProcessor<SimpleSecurityContext>().apply {
            jwsKeySelector = JWSVerificationKeySelector(JWSAlgorithm.ES256, jwkSource)
        }
    }
}

/**
 * Verify an end-user JWT issued by torii.
 *
 * Enforces ES256, strict `iss` equality, presence of `sub`, `iat`, `exp`,
 * and `iss`, and exp checks with [leewaySeconds] clock tolerance.
 *
 * The first call for a new issuer warms the JWKS cache; subsequent calls
 * are networkless. JWKS keys rotate automatically by `kid`.
 *
 * @param token Compact-serialized JWT (the value after `Bearer `).
 * @param issuer Expected issuer URL, e.g. `https://acme.torii.so`.
 *   Strict equality — required.
 * @param audience Optional `aud` claim to enforce. torii tokens do not set
 *   `aud` today, so leaving this `null` skips the check.
 * @param leewaySeconds Clock skew tolerance in seconds. Defaults to 30.
 * @throws ToriiAuthException if verification fails for any reason.
 */
@JvmOverloads
public fun verifyToken(
    token: String,
    issuer: String,
    audience: String? = null,
    leewaySeconds: Long = DEFAULT_LEEWAY_SECONDS,
): Auth {
    if (token.isBlank()) {
        throw ToriiAuthException("verifyToken: token must be a non-empty string")
    }
    if (issuer.isBlank()) {
        throw ToriiAuthException("verifyToken: `issuer` is required")
    }

    val normalizedIssuer = normalizeIssuer(issuer)
    val processor = processorForIssuer(normalizedIssuer)

    val parsed: SignedJWT = try {
        SignedJWT.parse(token)
    } catch (e: ParseException) {
        throw ToriiAuthException("JWT verification failed: token is not a valid signed JWT", e)
    }

    // Build a per-call claims verifier so clock leeway is configurable.
    val expectedClaims = JWTClaimsSet.Builder().issuer(normalizedIssuer).apply {
        if (audience != null) audience(audience)
    }.build()
    val required = if (audience != null) {
        setOf("sub", "iat", "exp", "iss", "aud")
    } else {
        setOf("sub", "iat", "exp", "iss")
    }
    val claimsVerifier = DefaultJWTClaimsVerifier<SimpleSecurityContext>(
        expectedClaims,
        required,
    )
    claimsVerifier.maxClockSkew = leewaySeconds.toInt()

    // Use a localized processor copy so the claims verifier is per-call —
    // we keep the cached JWKS / key selector on the shared processor and
    // run claims validation manually after signature check passes.
    val claims: JWTClaimsSet = try {
        processor.process(parsed, SimpleSecurityContext())
    } catch (e: BadJWTException) {
        throw ToriiAuthException("JWT verification failed: ${e.message}", e)
    } catch (e: BadJOSEException) {
        throw ToriiAuthException("JWT verification failed: ${e.message}", e)
    } catch (e: JOSEException) {
        throw ToriiAuthException("JWT verification failed: ${e.message}", e)
    } catch (e: ParseException) {
        throw ToriiAuthException("JWT verification failed: ${e.message}", e)
    }

    try {
        claimsVerifier.verify(claims, SimpleSecurityContext())
    } catch (e: BadJWTException) {
        throw ToriiAuthException("JWT verification failed: ${e.message}", e)
    }

    val userId: String = claims.subject
        ?: throw ToriiAuthException("JWT is missing required claim: sub")
    val iss: String = claims.issuer
        ?: throw ToriiAuthException("JWT is missing required claim: iss")
    if (claims.issueTime == null) {
        throw ToriiAuthException("JWT is missing required claim: iat")
    }
    if (claims.expirationTime == null) {
        throw ToriiAuthException("JWT is missing required claim: exp")
    }
    val environmentId: String = (claims.getClaim("pid") as? String)
        ?: throw ToriiAuthException("JWT is missing required claim: pid")

    val emailVerified = claims.getBooleanClaim("email_verified") ?: false
    // Default true when the claim is absent.
    val profileComplete = claims.getBooleanClaim("profile_complete") ?: true
    val impersonating = claims.getBooleanClaim("impersonating") ?: false
    val locale = claims.getClaim("locale") as? String

    return Auth(
        userId = userId,
        environmentId = environmentId,
        issuer = iss,
        emailVerified = emailVerified,
        profileComplete = profileComplete,
        impersonating = impersonating,
        locale = locale,
        raw = claims.claims,
    )
}

/**
 * Test-only: clear the JWKS cache. Production code should never call this —
 * the underlying nimbus-jose-jwt `JWKSource` handles rotation via `kid`
 * lookup automatically.
 */
@JvmName("clearJwksCacheForTests")
internal fun clearJwksCacheForTests() {
    processors.clear()
}

