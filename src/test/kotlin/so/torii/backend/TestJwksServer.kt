package so.torii.backend

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.Curve
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jose.jwk.KeyUse
import com.nimbusds.jose.jwk.gen.ECKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.time.Instant
import java.util.Date

/**
 * In-process HTTPS-free JWKS server used by JWT tests. Serves the JWKS at
 * `/_torii/.well-known/jwks.json` so the SDK's hard-coded path lines up.
 */
internal class TestJwksServer(
    private val jwks: JWKSet,
) {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)

    var requestCount: Int = 0
        private set

    fun start() {
        server.createContext("/_torii/.well-known/jwks.json") { exchange ->
            requestCount++
            val bytes = jwks.toPublicJWKSet().toString().toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    fun stop() {
        server.stop(0)
    }

    fun issuer(): String = "http://127.0.0.1:${server.address.port}"
}

internal object TestKeys {
    fun generate(kid: String = "test-key-1"): ECKey =
        ECKeyGenerator(Curve.P_256)
            .keyID(kid)
            .keyUse(KeyUse.SIGNATURE)
            .algorithm(JWSAlgorithm.ES256)
            .generate()
}

/** Build & sign a JWT with sensible torii-flavoured defaults. */
internal data class TokenBuilder(
    val key: ECKey,
    val issuer: String,
    val subject: String = "11111111-1111-1111-1111-111111111111",
    val pid: String? = "22222222-2222-2222-2222-222222222222",
    val expiresAt: Instant = Instant.now().plusSeconds(300),
    val issuedAt: Instant = Instant.now().minusSeconds(5),
    val emailVerified: Boolean? = null,
    val profileComplete: Boolean? = null,
    val impersonating: Boolean? = null,
    val locale: String? = null,
    val audience: String? = null,
    val extraClaims: Map<String, Any> = emptyMap(),
    val omitSub: Boolean = false,
    val omitIss: Boolean = false,
    val signingKey: ECKey = key,
) {
    fun sign(): String {
        val builder = JWTClaimsSet.Builder()
        if (!omitSub) builder.subject(subject)
        if (!omitIss) builder.issuer(issuer)
        if (pid != null) builder.claim("pid", pid)
        builder.expirationTime(Date.from(expiresAt))
        builder.issueTime(Date.from(issuedAt))
        if (emailVerified != null) builder.claim("email_verified", emailVerified)
        if (profileComplete != null) builder.claim("profile_complete", profileComplete)
        if (impersonating != null) builder.claim("impersonating", impersonating)
        if (locale != null) builder.claim("locale", locale)
        if (audience != null) builder.audience(audience)
        extraClaims.forEach { (k, v) -> builder.claim(k, v) }

        val header = JWSHeader.Builder(JWSAlgorithm.ES256).keyID(signingKey.keyID).build()
        val jwt = SignedJWT(header, builder.build())
        jwt.sign(ECDSASigner(signingKey))
        return jwt.serialize()
    }
}
