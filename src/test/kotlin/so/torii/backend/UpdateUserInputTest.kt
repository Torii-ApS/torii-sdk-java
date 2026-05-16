package so.torii.backend

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

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
        val body = UpdateUserInput(name = PatchValue.Set("Ada")).toJsonObject().toString()
        assertEquals("""{"name":"Ada"}""", body)
    }

    @Test
    fun `Set with null emits the key with explicit null`() {
        val body = UpdateUserInput(phone = PatchValue.Set(null)).toJsonObject().toString()
        assertEquals("""{"phone":null}""", body)
    }

    @Test
    fun `NotIncluded by default produces an empty object`() {
        val body = UpdateUserInput().toJsonObject().toString()
        assertEquals("""{}""", body)
    }

    @Test
    fun `mixed Set value Set null and NotIncluded preserves intent`() {
        val body = UpdateUserInput(
            name = PatchValue.Set("Ada"),
            phone = PatchValue.Set(null),
            address = PatchValue.NotIncluded,
        ).toJsonObject().toString()
        assertEquals("""{"name":"Ada","phone":null}""", body)
    }

    @Test
    fun `dateOfBirth Set serialises as ISO date string`() {
        val body = UpdateUserInput(
            dateOfBirth = PatchValue.Set(LocalDate.of(1815, 12, 10)),
        ).toJsonObject().toString()
        assertEquals("""{"dateOfBirth":"1815-12-10"}""", body)
    }

    @Test
    fun `Java-style static factories produce equivalent wire output`() {
        val body = UpdateUserInput(
            name = PatchValue.set("Ada"),
            phone = PatchValue.set(null),
            address = PatchValue.omit(),
        ).toJsonObject().toString()
        assertEquals("""{"name":"Ada","phone":null}""", body)
    }
}
