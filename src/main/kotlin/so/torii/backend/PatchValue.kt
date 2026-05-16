package so.torii.backend

/**
 * Tri-state wrapper for PATCH body fields. Mirrors the server-side
 * `com.github.goodcode.gated.shared.PatchValue<T>` so customer-facing
 * intent translates 1:1 to wire semantics.
 *
 * - [Set] with a non-null value → emit JSON key with the value → server updates the field
 * - [Set] with a null value     → emit JSON key with explicit null → server clears the field
 * - [NotIncluded] (the default) → omit the JSON key entirely → server leaves the field unchanged
 *
 * ```kotlin
 * client.users.update(id, UpdateUserInput(
 *     name = PatchValue.Set("Ada"),   // -> {"name":"Ada"}    update
 *     phone = PatchValue.Set(null),   // -> {"phone":null}    clear
 *     // address omitted              // -> key not present    leave alone
 * ))
 * ```
 *
 * Java callers use the static factories: `PatchValue.set(value)` and
 * `PatchValue.omit()`. `PatchValue.set(null)` clears the field.
 */
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
