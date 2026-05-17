package so.torii.backend

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * Patches for [UsersClient.update]. Each field uses [PatchValue] so callers
 * can distinguish three states:
 *
 *  - [PatchValue.NotIncluded] (the default) → leave the field unchanged
 *  - [PatchValue.Set] with a value          → update the field to that value
 *  - [PatchValue.Set] with `null`           → clear the field (server-side null)
 *
 * The inner `T` is nullable (`String?`, `LocalDate?`) precisely so `Set(null)`
 * is the canonical "clear" — matching the server-side Kotlin model where
 * `Included(null)` carries the same meaning. Java callers use the static
 * factory `PatchValue.set(value)`; passing `null` clears.
 */
public data class UpdateUserInput(
    val name: PatchValue<String?> = PatchValue.NotIncluded,
    val phone: PatchValue<String?> = PatchValue.NotIncluded,
    val locale: PatchValue<String?> = PatchValue.NotIncluded,
    val address: PatchValue<String?> = PatchValue.NotIncluded,
    val dateOfBirth: PatchValue<LocalDate?> = PatchValue.NotIncluded,
)

/**
 * Serialise an [UpdateUserInput] to the exact wire body the torii server
 * expects.
 *
 * - [PatchValue.Set] with a value → `"key": value`
 * - [PatchValue.Set] with null    → `"key": null`
 * - [PatchValue.NotIncluded]      → key omitted entirely
 *
 * This is the SDK's tri-state contract — the generated `UpdateUserRequest`
 * DTO can only express two states (value vs null) because the server's
 * OpenAPI spec strips PatchValue's tri-state at the schema layer.
 */
internal fun UpdateUserInput.toJsonObject(): JsonObject = buildJsonObject {
    putPatch("name", name) { JsonPrimitive(it) }
    putPatch("phone", phone) { JsonPrimitive(it) }
    putPatch("locale", locale) { JsonPrimitive(it) }
    putPatch("address", address) { JsonPrimitive(it) }
    putPatch("dateOfBirth", dateOfBirth) { JsonPrimitive(it.toString()) }
}

private inline fun <T : Any> kotlinx.serialization.json.JsonObjectBuilder.putPatch(
    key: String,
    value: PatchValue<T?>,
    encode: (T) -> JsonPrimitive,
) {
    when (value) {
        is PatchValue.Set<T?> -> {
            val inner = value.value
            if (inner == null) put(key, JsonNull) else put(key, encode(inner))
        }
        PatchValue.NotIncluded -> Unit
    }
}
