@file:JvmName("AuthenticateRequest")

package so.torii.backend

// Framework-agnostic request authenticator. Servlet/Spring/Ktor adapters
// adapt their request shape to a `Map<String, List<String>>` view of headers;
// everything else flows through verifyToken. Keeping this layer thin means
// per-framework adapters can be ~20 lines each.

private const val DEFAULT_HEADER = "Authorization"

private val BEARER_REGEX = Regex("""^Bearer\s+(.+)$""", RegexOption.IGNORE_CASE)

/**
 * Read a torii end-user JWT from a request's `Authorization: Bearer ...`
 * header, then verify it.
 *
 * Headers are looked up case-insensitively. If the gateway forwards the
 * token in a different header, pass [header].
 *
 * @param headers HTTP request headers. The map's values are lists to match
 *   multi-valued header semantics; the first non-blank value of the matched
 *   header is used.
 * @param issuer Expected issuer URL — required, strict equality.
 * @param audience Optional `aud` claim to enforce.
 * @param leewaySeconds Clock skew tolerance in seconds (default 30).
 * @param header Header to read the bearer token from (default `Authorization`).
 * @throws ToriiAuthException if the header is missing, malformed, or the
 *   JWT fails verification.
 */
@JvmOverloads
public fun authenticateRequest(
    headers: Map<String, List<String>>,
    issuer: String,
    audience: String? = null,
    leewaySeconds: Long = 30,
    header: String = DEFAULT_HEADER,
): Auth {
    val raw = readHeader(headers, header)
        ?: throw ToriiAuthException("Missing $header header")
    val match = BEARER_REGEX.matchEntire(raw)
        ?: throw ToriiAuthException("$header header is not in 'Bearer <token>' form")
    val token = match.groupValues[1].trim()
    return verifyToken(token, issuer, audience, leewaySeconds)
}

private fun readHeader(headers: Map<String, List<String>>, name: String): String? {
    val target = name.lowercase()
    for ((key, values) in headers) {
        if (key.lowercase() != target) continue
        for (v in values) {
            if (v.isNotBlank()) return v
        }
    }
    return null
}
