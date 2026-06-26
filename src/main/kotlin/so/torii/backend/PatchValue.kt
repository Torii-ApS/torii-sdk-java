package so.torii.backend

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Tri-state wrapper for PATCH/search body fields, so customer-facing intent
 * translates 1:1 to wire semantics.
 *
 * - [Set] with a non-null value → emit JSON key with the value → server updates the field
 * - [Set] with a null value     → emit JSON key with explicit null → server clears the field
 * - [NotIncluded] (the default) → omit the JSON key entirely → server leaves the field unchanged
 *
 * ```kotlin
 * client.users.update(id, UpdateUserRequest(
 *     firstName = PatchValue.Set("Ada"),  // -> {"firstName":"Ada"}  update
 *     lastName = PatchValue.Set(null),    // -> {"lastName":null}    clear
 *     // locale omitted                   // -> key not present      leave alone
 * ))
 * ```
 *
 * The omit state relies on the generated `Serializer`'s `encodeDefaults = false`:
 * a field left at its [NotIncluded] default is dropped from the body. The
 * generated request models carry these fields directly (the regen post-process
 * rewrites every nullable `String` request field to `PatchValue<String?>`), so a
 * new tri-state field flows through with no hand-written mapping.
 *
 * Java callers use the static factories: `PatchValue.set(value)` and
 * `PatchValue.omit()`. `PatchValue.set(null)` clears the field.
 */
@Serializable(with = PatchValueSerializer::class)
public sealed interface PatchValue<out T> {
    public data class Set<out T>(val value: T) : PatchValue<T>

    public object NotIncluded : PatchValue<Nothing>

    public companion object {
        @JvmStatic
        public fun <T> set(value: T): PatchValue<T> = Set(value)

        @JvmStatic
        public fun omit(): PatchValue<Nothing> = NotIncluded
    }
}

/**
 * Generic kotlinx serializer for [PatchValue]. The enclosing model omits a field
 * left at its [PatchValue.NotIncluded] default (`encodeDefaults = false`), so this
 * serializer only ever encodes the [PatchValue.Set] case: it emits the inner value
 * straight through the element serializer (a value, or JSON `null` for a cleared
 * field). On decode, a present key becomes [PatchValue.Set]; an absent key falls
 * back to the property's [PatchValue.NotIncluded] default.
 */
public class PatchValueSerializer<T>(private val element: KSerializer<T>) : KSerializer<PatchValue<T>> {
    override val descriptor: SerialDescriptor = element.descriptor

    override fun serialize(
        encoder: Encoder,
        value: PatchValue<T>,
    ) {
        when (value) {
            is PatchValue.Set -> encoder.encodeSerializableValue(element, value.value)
            PatchValue.NotIncluded ->
                throw IllegalStateException(
                    "PatchValue.NotIncluded must be omitted (Serializer uses encodeDefaults=false); it is never serialized.",
                )
        }
    }

    override fun deserialize(decoder: Decoder): PatchValue<T> = PatchValue.Set(decoder.decodeSerializableValue(element))
}
