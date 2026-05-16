package so.torii.backend

import so.torii.backend.generated.model.UserResponse
import so.torii.backend.generated.model.UserSessionResponse

// Re-export generated DTOs under stable, public type names. Re-running
// openapi-generator can change the *generated* names, but the public
// `User` / `Session` aliases stay stable for SDK consumers.

public typealias User = UserResponse
public typealias UserStatus = UserResponse.Status
public typealias UserLocale = UserResponse.Locale
public typealias Session = UserSessionResponse

/**
 * Cursor-paginated page of items. `nextCursor` is non-null when [hasMore]
 * is true.
 */
public data class CursorPage<T>(
    val items: List<T>,
    val nextCursor: String?,
    val hasMore: Boolean,
)

/**
 * Re-export of the generated `ProblemDetail` (RFC 7807) under a stable,
 * package-level alias. All torii API errors that ship a body use this shape;
 * `ToriiApiException.body` deserializes to this when present.
 */
public typealias ToriiProblemDetail = so.torii.backend.generated.model.ProblemDetail
