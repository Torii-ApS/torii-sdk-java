package so.torii.backend

/**
 * Thrown when the torii backend API returns a non-2xx response.
 *
 * `status` is the HTTP status, `code` and `supportId` are extracted from
 * Problem-style error bodies when present, and `body` carries the raw
 * parsed JSON body for callers that need to inspect custom fields.
 */
class ToriiApiException
@JvmOverloads
constructor(
    message: String,
    val status: Int,
    val code: String? = null,
    val supportId: String? = null,
    val body: Any? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Thrown when JWT verification or `Authorization` header parsing fails.
 *
 * This is intentionally distinct from [ToriiApiException] — auth failures
 * are user-input errors (bad token, wrong issuer, expired), not server
 * errors. Spring Security adapters should translate this into a 401.
 */
class ToriiAuthException
@JvmOverloads
constructor(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
