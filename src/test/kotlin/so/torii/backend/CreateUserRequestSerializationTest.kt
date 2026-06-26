package so.torii.backend

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import so.torii.backend.generated.infrastructure.Serializer
import so.torii.backend.generated.model.CreateUserRequest

/**
 * Guards that [CreateUserRequest] serialises through the generated kotlinx
 * module. The three metadata bags are required objects and must reach the wire
 * as JSON objects (`{}`), never arrays or a contextual-serializer failure —
 * the regenerated spec marks them `Map<String, JsonElement>` so the serializer
 * is resolved at compile time.
 */
class CreateUserRequestSerializationTest {
    private val json = Serializer.kotlinxSerializationJson

    @Test
    fun `empty metadata bags serialise as JSON objects`() {
        val body = CreateUserRequest(
            publicMetadata = JsonObject(emptyMap()),
            privateMetadata = JsonObject(emptyMap()),
            unsafeMetadata = JsonObject(emptyMap()),
            email = "ada@example.com",
        )
        val wire = json.encodeToString(CreateUserRequest.serializer(), body)
        assertTrue(wire.contains("\"publicMetadata\":{}"), wire)
        assertTrue(wire.contains("\"privateMetadata\":{}"), wire)
        assertTrue(wire.contains("\"unsafeMetadata\":{}"), wire)
        assertTrue(wire.contains("\"email\":\"ada@example.com\""), wire)
    }

    @Test
    fun `populated bag serialises its entries as an object`() {
        val body = CreateUserRequest(
            publicMetadata = JsonObject(mapOf("tier" to JsonPrimitive("pro"))),
            privateMetadata = JsonObject(emptyMap()),
            unsafeMetadata = JsonObject(emptyMap()),
        )
        val wire = json.encodeToString(CreateUserRequest.serializer(), body)
        assertTrue(wire.contains("\"publicMetadata\":{\"tier\":\"pro\"}"), wire)
    }
}
