# torii backend SDK — Java / Kotlin

Kotlin-first, Java-interoperable backend SDK for [torii](https://torii.so) — verify end-user JWTs without a per-request round trip and manage users from your JVM server.

> **v0.x — API may still change.**
> Maven coordinates: **`so.torii:torii-backend:0.0.1`**
> JVM target: **17+**

## Setup

1. Sign in to [app.torii.so](https://app.torii.so) and from your dashboard copy:
   - your **issuer URL** (e.g. `https://acme.torii.so`)
   - a **secret key** (`sk_test_…` for development, `sk_live_…` for production)

2. Add the dependency.

   Gradle (Kotlin DSL):

   ```kotlin
   dependencies {
       implementation("so.torii:torii-backend:0.0.1")
   }
   ```

   Maven:

   ```xml
   <dependency>
       <groupId>so.torii</groupId>
       <artifactId>torii-backend</artifactId>
       <version>0.0.1</version>
   </dependency>
   ```

3. Verify an end-user JWT:

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

   The first call per issuer fetches `${issuer}/_torii/.well-known/jwks.json` and caches the JWKS in-process. Subsequent calls are networkless and pick up key rotation by `kid`.

   From Java:

   ```java
   Auth auth = VerifyToken.verifyToken(bearerToken, "https://acme.torii.so");
   ```

4. Call the backend REST API:

   ```kotlin
   import so.torii.backend.ToriiClient

   val torii = ToriiClient.create(secretKey = System.getenv("TORII_SECRET_KEY"))
   val user = torii.users.get(userId)
   ```

   Default base URL is `https://api.torii.so`. Override for staging or testing environments:

   ```kotlin
   ToriiClient.create(secretKey = "...", apiUrl = "https://api.staging.torii.so")
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
import java.util.UUID

val user = torii.users.get(UUID.fromString("11111111-1111-1111-1111-111111111111"))
val page = torii.users.list()
val newUser = torii.users.create(CreateUserInput(email = "ada@example.com"))
torii.users.ban(user.id)
torii.sessions.revokeAllForUser(user.id)
```

### PATCH semantics

`UpdateUserInput` fields are tri-state via [`PatchValue<T>`](src/main/kotlin/so/torii/backend/PatchValue.kt):

- `PatchValue.Set(value)` — emit the key with the value → server updates the field
- `PatchValue.Set(null)` — emit the key with `null` → server clears the field
- `PatchValue.NotIncluded` (the default) — omit the key entirely → server leaves the field unchanged

```kotlin
torii.users.update(
    userId = id,
    patches = UpdateUserInput(
        name = PatchValue.Set("Ada"),   // -> {"name":"Ada"}
        phone = PatchValue.Set(null),   // -> {"phone":null}
        // address omitted              // -> key not present in body
    ),
)
```

From Java, use the static factories:

```java
toriiClient.getUsers().update(userId, new UpdateUserInput(
    PatchValue.set("Ada"),  // name
    PatchValue.set(null),   // phone
    PatchValue.omit(),      // avatarUrl
    PatchValue.omit(),      // locale
    PatchValue.omit(),      // address
    PatchValue.omit()       // dateOfBirth
));
```

## Errors

- `ToriiAuthException` — JWT verification or `Authorization` parsing failure. Translate to HTTP 401 in your app.
- `ToriiApiException` — non-2xx response from `api.torii.so`. Exposes `status`, `code` (Problem-style), `supportId`, and the raw `body`.

## Regenerating the REST client

The REST client is generated from `spec/server-v1.json` by `openapi-generator` (Gradle plugin). To refresh:

```bash
./gradlew regenerateOpenApi
```

Generated sources live under `src/main/kotlin/so/torii/backend/generated/` and are committed. The task above stages a fresh generation into `build/openapi-staging/` and syncs only the Kotlin sources into the committed location.

## License

MIT
