package so.torii.backend

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.time.LocalDate

/**
 * Patches for [UsersClient.update]. Each field uses [PatchValue] so callers
 * can distinguish "leave unchanged" ([PatchValue.NotIncluded], the default),
 * "set to value" ([PatchValue.Set]) and "clear to null" ([PatchValue.Clear]).
 *
 * Java callers should use the static factories on [PatchValue]:
 * `PatchValue.set("Ada")`, `PatchValue.clear()`, `PatchValue.omit()`.
 */
public data class UpdateUserInput(
    val name: PatchValue<String> = PatchValue.NotIncluded,
    val phone: PatchValue<String> = PatchValue.NotIncluded,
    val avatarUrl: PatchValue<String> = PatchValue.NotIncluded,
    val locale: PatchValue<String> = PatchValue.NotIncluded,
    val address: PatchValue<String> = PatchValue.NotIncluded,
    val dateOfBirth: PatchValue<LocalDate> = PatchValue.NotIncluded,
)

/**
 * Serialise an [UpdateUserInput] to the exact wire body the torii server
 * expects.
 *
 * - [PatchValue.Set] entries appear as `"key": value`.
 * - [PatchValue.Clear] entries appear as `"key": null`.
 * - [PatchValue.NotIncluded] entries are omitted entirely.
 *
 * This is what gives the SDK tri-state PATCH semantics even though the
 * generated `UpdateUserRequest` DTO can only express two states (value vs
 * `null`).
 */
internal fun UpdateUserInput.toJsonObject(): JsonObject = buildJsonObject {
    putPatch("name", name) { JsonPrimitive(it) }
    putPatch("phone", phone) { JsonPrimitive(it) }
    putPatch("avatarUrl", avatarUrl) { JsonPrimitive(it) }
    putPatch("locale", locale) { JsonPrimitive(it) }
    putPatch("address", address) { JsonPrimitive(it) }
    putPatch("dateOfBirth", dateOfBirth) { JsonPrimitive(it.toString()) }
}

private inline fun <T> kotlinx.serialization.json.JsonObjectBuilder.putPatch(
    key: String,
    value: PatchValue<T>,
    encode: (T) -> JsonPrimitive,
) {
    when (value) {
        is PatchValue.Set<T> -> put(key, encode(value.value))
        PatchValue.Clear -> put(key, JsonNull)
        PatchValue.NotIncluded -> Unit
    }
}
