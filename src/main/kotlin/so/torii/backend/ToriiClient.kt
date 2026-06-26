package so.torii.backend

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import so.torii.backend.generated.infrastructure.ClientException
import so.torii.backend.generated.infrastructure.ServerException
import so.torii.backend.generated.api.ServerSessionsApi
import so.torii.backend.generated.api.ServerUsersApi
import so.torii.backend.generated.model.CreateUserRequest
import so.torii.backend.generated.model.ServerUserResponse
import so.torii.backend.generated.model.ServerUserSearchRequest
import java.time.OffsetDateTime
import java.util.UUID

/**
 * Backend SDK entry point. Construct via [create] (or [Builder] from Java)
 * and use the [users] / [sessions] sub-clients.
 *
 * ```kotlin
 * val torii = ToriiClient.create(secretKey = System.getenv("TORII_SECRET_KEY"))
 * val user = torii.users.get(UUID.fromString("..."))
 * ```
 *
 * The default API URL is `https://api.torii.so`. Override for staging or
 * self-hosted deployments.
 *
 * `ToriiClient` is safe to share across threads — it holds no per-call
 * state. Construct once per process.
 */
public class ToriiClient internal constructor(
    public val users: UsersClient,
    public val sessions: SessionsClient,
) {
    public companion object {
        public const val DEFAULT_API_URL: String = "https://api.torii.so"

        /**
         * Create a [ToriiClient].
         *
         * @param secretKey Backend secret key, e.g. `sk_live_...` / `sk_test_...`.
         * @param apiUrl API base URL. Defaults to [DEFAULT_API_URL].
         * @param okHttpClient Optional pre-configured OkHttp client to share
         *   a connection pool with other clients. The SDK adds its own
         *   `Authorization` interceptor on top, so consumers should not
         *   add a Bearer auth interceptor of their own.
         */
        @JvmStatic
        @JvmOverloads
        public fun create(
            secretKey: String,
            apiUrl: String = DEFAULT_API_URL,
            okHttpClient: OkHttpClient? = null,
        ): ToriiClient {
            require(secretKey.isNotBlank()) { "ToriiClient.create: `secretKey` is required" }
            require(apiUrl.isNotBlank()) { "ToriiClient.create: `apiUrl` must not be blank" }
            val base = apiUrl.trimEnd('/')
            val client = (okHttpClient ?: OkHttpClient())
                .newBuilder()
                .addInterceptor(BearerAuthInterceptor(secretKey))
                .build()
            val usersApi = ServerUsersApi(base, client)
            val sessionsApi = ServerSessionsApi(base, client)
            return ToriiClient(
                users = UsersClient(usersApi),
                sessions = SessionsClient(sessionsApi),
            )
        }
    }
}

private class BearerAuthInterceptor(private val secretKey: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request().newBuilder()
            .header("Authorization", "Bearer $secretKey")
            .header("Accept", "application/json")
            .build()
        return chain.proceed(req)
    }
}

private val errorBodyJson = Json { ignoreUnknownKeys = true; isLenient = true }

private inline fun <T> withApiErrorMapping(path: String, block: () -> T): T {
    return try {
        block()
    } catch (e: ClientException) {
        throw mapApiException(path, e.statusCode, e.message, e.response, e)
    } catch (e: ServerException) {
        throw mapApiException(path, e.statusCode, e.message, e.response, e)
    }
}

private fun mapApiException(
    path: String,
    status: Int,
    message: String?,
    rawBody: Any?,
    cause: Throwable?,
): ToriiApiException {
    val (code, supportId, detailMessage) = extractErrorFields(rawBody)
    val finalMessage = detailMessage
        ?: message?.takeUnless { it.isBlank() }
        ?: "torii $path failed ($status)"
    return ToriiApiException(
        message = finalMessage,
        status = status,
        code = code,
        supportId = supportId,
        body = rawBody,
        cause = cause,
    )
}

private data class ErrorFields(val code: String?, val supportId: String?, val message: String?)

private fun extractErrorFields(body: Any?): ErrorFields {
    val text = when (body) {
        null -> null
        is String -> body
        is ByteArray -> body.toString(Charsets.UTF_8)
        else -> body.toString()
    } ?: return ErrorFields(null, null, null)
    if (text.isBlank()) return ErrorFields(null, null, null)
    val parsed: JsonElement = try {
        errorBodyJson.parseToJsonElement(text)
    } catch (_: Exception) {
        return ErrorFields(null, null, null)
    }
    val obj = parsed as? JsonObject ?: return ErrorFields(null, null, null)
    fun str(key: String): String? = obj[key]?.let {
        if (it is JsonPrimitive && it.isString) it.content else null
    }
    val code = str("code")
    val supportId = str("supportId") ?: str("support_id")
    val message = str("detail") ?: str("title") ?: str("message")
    return ErrorFields(code, supportId, message)
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

/**
 * Options for [UsersClient.list]. Mirrors `/api/server/v1/users/search`.
 *
 * Both `name` and `email` are substring matches; `statuses` filters by
 * one or more [UserStatus] values; `createdAfter` / `createdBefore`
 * bound the search by creation timestamp.
 */
public data class ListUsersOptions(
    val limit: Int? = null,
    val cursor: UUID? = null,
    val name: String? = null,
    val email: String? = null,
    val statuses: List<UserStatus>? = null,
    val createdAfter: OffsetDateTime? = null,
    val createdBefore: OffsetDateTime? = null,
)

/** Options for [UsersClient.create]. */
public data class CreateUserInput
@JvmOverloads
constructor(
    val email: String? = null,
    val password: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    // Metadata bags — optional; default to empty {} on send (a new user has none to clobber).
    val publicMetadata: JsonObject? = null,
    val privateMetadata: JsonObject? = null,
    val unsafeMetadata: JsonObject? = null,
)

public class UsersClient internal constructor(
    private val api: ServerUsersApi,
) {
    @JvmOverloads
    public fun list(options: ListUsersOptions = ListUsersOptions()): CursorPage<User> {
        val req = ServerUserSearchRequest(
            // ListUsersOptions exposes name/email as plain String? (null = no filter);
            // map onto the generated tri-state field (a value sets the filter, absent
            // omits it). The "filter for users with no name" case is reachable by
            // constructing ServerUserSearchRequest directly with PatchValue.Set(null).
            name = options.name?.let { PatchValue.Set(it) } ?: PatchValue.NotIncluded,
            email = options.email?.let { PatchValue.Set(it) } ?: PatchValue.NotIncluded,
            statuses = options.statuses?.mapTo(mutableSetOf()) { mapStatusToSearch(it) },
            createdAfter = options.createdAfter,
            createdBefore = options.createdBefore,
        )
        val page = withApiErrorMapping("GET /api/server/v1/users/search") {
            runBlocking {
                api.searchUsers(
                    limit = options.limit,
                    cursor = options.cursor,
                    serverUserSearchRequest = req,
                )
            }
        }
        return CursorPage(
            items = page.items.orEmpty(),
            nextCursor = page.nextCursor?.toString(),
            hasMore = page.hasMore,
        )
    }

    public fun get(userId: UUID): User =
        withApiErrorMapping("GET /api/server/v1/users/$userId") {
            runBlocking { api.getUser(userId) }
        }

    public fun create(input: CreateUserInput): User {
        val body = CreateUserRequest(
            publicMetadata = input.publicMetadata ?: JsonObject(emptyMap()),
            privateMetadata = input.privateMetadata ?: JsonObject(emptyMap()),
            unsafeMetadata = input.unsafeMetadata ?: JsonObject(emptyMap()),
            email = input.email,
            password = input.password,
            firstName = input.firstName,
            lastName = input.lastName,
        )
        return withApiErrorMapping("POST /api/server/v1/users") {
            runBlocking { api.createUser(body) }
        }
    }

    /**
     * PATCH the user identified by [userId] with the supplied tri-state [request].
     *
     * Routes through the generated client. The [UpdateUserRequest] model carries
     * its tri-state fields as [PatchValue], so a field at its
     * `PatchValue.NotIncluded` default is omitted (leave unchanged),
     * `PatchValue.Set(value)` sets it, and `PatchValue.Set(null)` clears it. The
     * generated `Serializer` (`encodeDefaults = false`) drops the omitted fields.
     */
    public fun update(userId: UUID, request: UpdateUserRequest): User =
        withApiErrorMapping("PATCH /api/server/v1/users/$userId") {
            runBlocking { api.updateUser(userId, request) }
        }

    public fun delete(userId: UUID) {
        withApiErrorMapping("DELETE /api/server/v1/users/$userId") {
            runBlocking { api.deleteUser(userId) }
        }
    }

    public fun ban(userId: UUID): User =
        withApiErrorMapping("POST /api/server/v1/users/$userId/ban") {
            runBlocking { api.banUser(userId) }
        }

    public fun unban(userId: UUID): User =
        withApiErrorMapping("POST /api/server/v1/users/$userId/unban") {
            runBlocking { api.unbanUser(userId) }
        }
}

// Map our public UserStatus (alias for ServerUserResponse.Status) to the
// generated search-request enum (a separate type with the same string
// values).
private fun mapStatusToSearch(
    status: UserStatus,
): ServerUserSearchRequest.Statuses = when (status) {
    ServerUserResponse.Status.ACTIVE -> ServerUserSearchRequest.Statuses.ACTIVE
    ServerUserResponse.Status.BANNED -> ServerUserSearchRequest.Statuses.BANNED
    ServerUserResponse.Status.DELETED -> ServerUserSearchRequest.Statuses.DELETED
}

// ---------------------------------------------------------------------------
// Sessions
// ---------------------------------------------------------------------------

public class SessionsClient internal constructor(
    private val api: ServerSessionsApi,
) {
    public fun listForUser(userId: UUID): List<Session> =
        withApiErrorMapping("GET /api/server/v1/users/$userId/sessions") {
            runBlocking { api.listSessions(userId) }
        }

    public fun revokeAllForUser(userId: UUID) {
        withApiErrorMapping("DELETE /api/server/v1/users/$userId/sessions") {
            runBlocking { api.revokeAllSessions(userId) }
        }
    }

    public fun revoke(userId: UUID, sessionId: UUID) {
        withApiErrorMapping("DELETE /api/server/v1/users/$userId/sessions/$sessionId") {
            runBlocking { api.revokeSession(userId, sessionId) }
        }
    }
}

