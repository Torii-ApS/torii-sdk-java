package so.torii.backend

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies [UpdateUserInput.toJsonObject] produces the exact wire body the
 * server expects:
 *
 *  - [PatchValue.Set] with a value → `"key": value`
 *  - [PatchValue.Set] with null    → `"key": null`
 *  - [PatchValue.NotIncluded]      → key omitted entirely
 *
 * The body builder is the single source of truth for PATCH semantics; if
 * these tests pass, customers' tri-state intent makes it to the wire.
 */
class UpdateUserInputTest {
    @Test
    fun `Set with value emits only the named key with that value`() {
        val body = UpdateUserInput(firstName = PatchValue.Set("Ada")).toJsonObject().toString()
        assertEquals("""{"firstName":"Ada"}""", body)
    }

    @Test
    fun `Set with null emits the key with explicit null`() {
        val body = UpdateUserInput(lastName = PatchValue.Set(null)).toJsonObject().toString()
        assertEquals("""{"lastName":null}""", body)
    }

    @Test
    fun `NotIncluded by default produces an empty object`() {
        val body = UpdateUserInput().toJsonObject().toString()
        assertEquals("""{}""", body)
    }

    @Test
    fun `mixed Set value Set null and NotIncluded preserves intent`() {
        val body = UpdateUserInput(
            firstName = PatchValue.Set("Ada"),
            lastName = PatchValue.Set(null),
            locale = PatchValue.NotIncluded,
        ).toJsonObject().toString()
        assertEquals("""{"firstName":"Ada","lastName":null}""", body)
    }

    @Test
    fun `unsafeMetadata Set emits the bag as a JSON object`() {
        val body = UpdateUserInput(
            unsafeMetadata = PatchValue.Set(JsonObject(mapOf("tier" to JsonPrimitive("pro")))),
        ).toJsonObject().toString()
        assertEquals("""{"unsafeMetadata":{"tier":"pro"}}""", body)
    }

    @Test
    fun `unsafeMetadata Set null clears the bag`() {
        val body = UpdateUserInput(
            unsafeMetadata = PatchValue.Set(null),
        ).toJsonObject().toString()
        assertEquals("""{"unsafeMetadata":null}""", body)
    }

    @Test
    fun `Java-style static factories produce equivalent wire output`() {
        val body = UpdateUserInput(
            firstName = PatchValue.set("Ada"),
            lastName = PatchValue.set(null),
            locale = PatchValue.omit(),
        ).toJsonObject().toString()
        assertEquals("""{"firstName":"Ada","lastName":null}""", body)
    }
}
