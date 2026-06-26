package so.torii.backend

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Patches for [UsersClient.update]. Each field uses [PatchValue] so callers
 * can distinguish three states:
 *
 *  - [PatchValue.NotIncluded] (the default) → leave the field unchanged
 *  - [PatchValue.Set] with a value          → update the field to that value
 *  - [PatchValue.Set] with `null`           → clear the field (server-side null)
 *
 * The inner `T` is nullable (`String?`) precisely so `Set(null)` is the
 * canonical "clear" — matching the server-side Kotlin model where
 * `Included(null)` carries the same meaning. `unsafeMetadata` is tri-state too:
 * omit to leave the server's metadata untouched (never clobbered), set a
 * [JsonObject] to replace it, or `Set(null)` to clear. Java callers use the
 * static factory `PatchValue.set(value)`; passing `null` clears.
 */
public data class UpdateUserInput(
    val firstName: PatchValue<String?> = PatchValue.NotIncluded,
    val lastName: PatchValue<String?> = PatchValue.NotIncluded,
    val locale: PatchValue<String?> = PatchValue.NotIncluded,
    val unsafeMetadata: PatchValue<JsonObject?> = PatchValue.NotIncluded,
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
    putPatch("firstName", firstName) { JsonPrimitive(it) }
    putPatch("lastName", lastName) { JsonPrimitive(it) }
    putPatch("locale", locale) { JsonPrimitive(it) }
    // unsafeMetadata is already a JsonObject; emit it (or null) directly.
    when (val m = unsafeMetadata) {
        is PatchValue.Set<JsonObject?> -> put("unsafeMetadata", m.value ?: JsonNull)
        PatchValue.NotIncluded -> Unit
    }
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
