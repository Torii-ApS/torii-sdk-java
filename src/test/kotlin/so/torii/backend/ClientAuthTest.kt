package so.torii.backend

import com.sun.net.httpserver.HttpServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * The secret key must reach the wire as `Authorization: Bearer <key>` on every
 * call — both the generated path (get) and the hand-rolled PATCH (update),
 * which share the same OkHttp client + auth interceptor.
 */
class ClientAuthTest {
    private lateinit var server: HttpServer
    private val lastAuth = AtomicReference<String?>()

    private val userJson = """
        {
          "id": "11111111-1111-1111-1111-111111111111",
          "environmentId": "22222222-2222-2222-2222-222222222222",
          "status": "active",
          "createdAt": "2024-01-01T00:00:00Z",
          "updatedAt": "2024-01-01T00:00:00Z",
          "publicMetadata": {},
          "privateMetadata": {},
          "unsafeMetadata": {}
        }
    """.trimIndent()

    @BeforeEach
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastAuth.set(exchange.requestHeaders.getFirst("Authorization"))
            val bytes = userJson.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @AfterEach
    fun stop() = server.stop(0)

    private fun client(): ToriiClient =
        ToriiClient.create(secretKey = "sk_test_abc", apiUrl = "http://127.0.0.1:${server.address.port}")

    @Test
    fun `generated call sends the bearer token`() {
        client().users.get(UUID.randomUUID())
        assertEquals("Bearer sk_test_abc", lastAuth.get())
    }

    @Test
    fun `hand-rolled PATCH sends the bearer token`() {
        client().users.update(UUID.randomUUID(), UpdateUserInput(firstName = PatchValue.Set("Ada")))
        assertEquals("Bearer sk_test_abc", lastAuth.get())
    }
}
