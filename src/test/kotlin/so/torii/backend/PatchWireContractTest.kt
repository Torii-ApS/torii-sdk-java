package so.torii.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import so.torii.backend.generated.infrastructure.Serializer
import so.torii.backend.generated.model.CreateUserRequest
import so.torii.backend.generated.model.ServerUserSearchRequest
import so.torii.backend.generated.model.UpdateUserMetadataRequest
import so.torii.backend.generated.model.UpdateUserRequest

/**
 * Wire-parity against the shared contract fixtures
 * (contract-tests/fixtures/patch-wire, vendored to test resources). For each
 * fixture we decode `expectedBody` into the generated request model and encode it
 * straight back through the generated `Serializer`; the round-trip must be
 * identical. This pins that the SDK emits the blessed bytes (an absent key stays
 * absent => leave, an explicit null stays null => clear, nested nulls survive =>
 * key delete), matching the server round-trip test and every other SDK.
 */
class PatchWireContractTest {
    private val json = Serializer.kotlinxSerializationJson

    private val serializers: Map<String, KSerializer<*>> =
        mapOf(
            "UpdateUserRequest" to UpdateUserRequest.serializer(),
            "CreateUserRequest" to CreateUserRequest.serializer(),
            "ServerUserSearchRequest" to ServerUserSearchRequest.serializer(),
            "UpdateUserMetadataRequest" to UpdateUserMetadataRequest.serializer(),
        )

    @TestFactory
    fun `SDK emits the blessed wire bytes for every fixture`(): List<DynamicTest> {
        val manifest = json.parseToJsonElement(readFixtures()).jsonObject
        return manifest.getValue("fixtures").jsonArray.map { it.jsonObject }.map { fixture ->
            val name = fixture.getValue("name").jsonPrimitive.content
            val schema = fixture.getValue("schema").jsonPrimitive.content
            val expected = fixture.getValue("expectedBody").jsonObject
            DynamicTest.dynamicTest(name) {
                val serializer = serializers[schema] ?: error("no serializer registered for schema $schema")
                @Suppress("UNCHECKED_CAST")
                val typed = serializer as KSerializer<Any?>
                val model = json.decodeFromJsonElement(typed, expected)
                val wire = json.encodeToJsonElement(typed, model) as JsonObject
                assertEquals(expected, wire, "wire mismatch for fixture '$name'")
            }
        }
    }

    private fun readFixtures(): String =
        requireNotNull(javaClass.getResourceAsStream("/patch-wire-fixtures.json")) {
            "patch-wire-fixtures.json not found on the test classpath"
        }.bufferedReader().use { it.readText() }
}
