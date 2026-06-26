package so.torii.backend

import so.torii.backend.generated.model.ServerUserResponse
import so.torii.backend.generated.model.UserSessionResponse

// Re-export generated DTOs under stable, public type names. Re-running
// openapi-generator can change the *generated* names, but the public
// `User` / `Session` aliases stay stable for SDK consumers.

public typealias User = ServerUserResponse
public typealias UserStatus = ServerUserResponse.Status
public typealias UserLocale = ServerUserResponse.Locale
public typealias Session = UserSessionResponse

/**
 * Tri-state PATCH body for [UsersClient.update], re-exported from the generated
 * package. Its nullable `String` fields are [PatchValue]: `PatchValue.Set(v)`
 * sets, `PatchValue.Set(null)` clears, and leaving a field at its
 * `PatchValue.NotIncluded` default omits it (server leaves it unchanged).
 */
public typealias UpdateUserRequest = so.torii.backend.generated.model.UpdateUserRequest

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
