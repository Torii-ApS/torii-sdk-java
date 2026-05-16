package so.torii.backend

/**
 * Verified torii end-user JWT, decoded into the fields most callers need.
 *
 * Returned from [verifyToken] and [authenticateRequest]. Custom claims live
 * on [raw] — anything not surfaced as a typed field is still accessible there.
 *
 * @property userId End-user ID (JWT `sub`). Always present.
 * @property environmentId Environment ID this token was issued in (JWT `pid`).
 * @property issuer Issuer (JWT `iss`) — the canonical FAPI URL for the env.
 * @property emailVerified True if the end-user has verified at least one email.
 * @property profileComplete True if required profile fields are all filled.
 *   Defaults to `true` if the claim is absent from the token.
 * @property impersonating True when an admin is impersonating this user.
 * @property locale End-user's preferred locale; `null` when not set.
 * @property raw Raw JWT payload — escape hatch for custom claims.
 */
data class Auth(
    val userId: String,
    val environmentId: String,
    val issuer: String,
    val emailVerified: Boolean,
    val profileComplete: Boolean,
    val impersonating: Boolean,
    val locale: String?,
    val raw: Map<String, Any?>,
)
