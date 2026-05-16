# torii backend SDK — Java / Kotlin

Kotlin-first, Java-interoperable backend SDK for [torii](https://torii.so).

- JWT verification (ES256, networkless after a one-time JWKS fetch, kid-rotated)
- `Authorization: Bearer` request authentication
- REST client for `/api/server/v1/**` (users, sessions)
- Pure Java/Kotlin — no framework dependency. Framework adapters (Spring Security, Ktor, Micronaut) will land in separate artifacts later.

> Maven coordinates: **`so.torii:torii-backend:0.0.1`**
> JVM target: **17+** (compatible with most production JVMs; the torii server itself runs on JDK 25 but the SDK should not require that)

## Install

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("so.torii:torii-backend:0.0.1")
}
```

### Maven

```xml
<dependency>
    <groupId>so.torii</groupId>
    <artifactId>torii-backend</artifactId>
    <version>0.0.1</version>
</dependency>
```

## Verify an end-user JWT

```kotlin
import so.torii.backend.verifyToken

val auth = verifyToken(
    token = bearerToken,
    issuer = "https://acme.torii.so",
)

println(auth.userId)          // JWT `sub`
println(auth.environmentId)   // JWT `pid`
println(auth.emailVerified)
```

The first call per issuer fetches `${issuer}/_torii/.well-known/jwks.json`
and caches the JWKS in-process. Subsequent calls are networkless and
automatically pick up key rotation by `kid`.

From Java:

```java
Auth auth = VerifyToken.verifyToken(bearerToken, "https://acme.torii.so");
```

## Authenticate an HTTP request

Framework-agnostic — pass a header map:

```kotlin
val auth = authenticateRequest(
    headers = mapOf("Authorization" to listOf("Bearer $token")),
    issuer = "https://acme.torii.so",
)
```

## REST client

```kotlin
import so.torii.backend.ToriiClient
import java.util.UUID

val torii = ToriiClient.create(secretKey = System.getenv("TORII_SECRET_KEY"))

val user = torii.users.get(UUID.fromString("11111111-1111-1111-1111-111111111111"))
val page = torii.users.list()
val newUser = torii.users.create(CreateUserInput(email = "ada@example.com"))
torii.users.ban(user.id)
torii.sessions.revokeAllForUser(user.id)
```

API base URL defaults to `https://api.torii.so`. Override for staging or
self-hosted:

```kotlin
ToriiClient.create(secretKey = "...", apiUrl = "https://api.staging.torii.so")
```

### PATCH semantics

`UpdateUserInput` fields are tri-state via [`PatchValue<T>`](src/main/kotlin/so/torii/backend/PatchValue.kt):

- `PatchValue.Set(value)` — emit the key with the value → server updates the field
- `PatchValue.Clear` — emit the key with `null` → server clears the field
- `PatchValue.NotIncluded` (the default) — omit the key entirely → server leaves the field unchanged

```kotlin
torii.users.update(
    userId = id,
    patches = UpdateUserInput(
        name = PatchValue.Set("Ada"),   // -> {"name":"Ada"}
        phone = PatchValue.Clear,       // -> {"phone":null}
        // address omitted              // -> key not present in body
    ),
)
```

The SDK builds the PATCH body directly from `UpdateUserInput` so the wire
JSON contains only the keys the caller mentioned — "leave alone" stays
distinct from "clear to null".

From Java, use the static factories:

```java
toriiClient.getUsers().update(userId, new UpdateUserInput(
    PatchValue.set("Ada"),  // name
    PatchValue.clear(),     // phone
    PatchValue.omit(),      // avatarUrl
    PatchValue.omit(),      // locale
    PatchValue.omit(),      // address
    PatchValue.omit()       // dateOfBirth
));
```

## Errors

- `ToriiAuthException` — JWT verification or `Authorization` parsing failure.
  Translate to HTTP 401 in your app.
- `ToriiApiException` — non-2xx response from `api.torii.so`. Exposes
  `status`, `code` (Problem-style), `supportId`, and the raw `body`.

## Webhooks

`verifyWebhook(...)` is a documented stub — torii's outbound webhook
subsystem ships under Torii-ApS/torii#424 Phase 0.5. Calling it today throws
`ToriiAuthException`. When the feature lands, callers won't need to change
their integration shape.

## Regenerating the REST client

The REST client is generated from `spec/server-v1.json` by
`openapi-generator` (Gradle plugin). To refresh:

```bash
./gradlew regenerateOpenApi
```

Generated sources live under `src/main/kotlin/so/torii/backend/generated/` and are committed (matching the other torii SDKs). The task above stages a fresh generation into `build/openapi-staging/` and syncs only the Kotlin sources into the committed location.

## Repository layout

```
torii-sdk-java/
├── build.gradle.kts            # kotlin("jvm"), openapi-generator, maven-publish
├── settings.gradle.kts
├── gradle.properties
├── spec/
│   └── server-v1.json          # OpenAPI source-of-truth
├── src/main/kotlin/so/torii/backend/
│   ├── Auth.kt                 # Verified-token DTO
│   ├── Errors.kt               # ToriiApiException, ToriiAuthException
│   ├── PatchValue.kt           # tri-state PATCH sealed interface
│   ├── UpdateUserInput.kt      # PATCH input + JsonObject serialiser
│   ├── ToriiClient.kt          # REST client wrapper + factory
│   ├── Types.kt                # User / Session typealiases + CursorPage
│   ├── VerifyToken.kt          # ES256 + JWKS-cached verifier
│   ├── AuthenticateRequest.kt  # Authorization-header helper
│   ├── VerifyWebhook.kt        # stub for Torii-ApS/torii#424 Phase 0.5
│   └── generated/              # openapi-generator output (committed)
└── src/test/kotlin/            # JUnit 5 tests with in-process JWKS server
```
