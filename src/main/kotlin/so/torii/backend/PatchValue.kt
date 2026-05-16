package so.torii.backend

/**
 * Tri-state wrapper for PATCH body fields. Mirrors the server-side
 * `com.github.goodcode.gated.shared.PatchValue<T>` so customer-facing
 * intent translates 1:1 to wire semantics.
 *
 * - [Set]         emit JSON key with value → server updates field
 * - [Clear]       emit JSON key with null  → server clears field
 * - [NotIncluded] omit JSON key            → server leaves field unchanged
 *
 * Default constructor argument on [UpdateUserInput] etc. is [NotIncluded]
 * so callers only mention the fields they want to touch.
 *
 * ```kotlin
 * client.users.update(id, UpdateUserInput(
 *     name = PatchValue.Set("Ada"),   // -> {"name":"Ada"}
 *     phone = PatchValue.Clear,       // -> {"phone":null}
 *     // address omitted              // -> key not present
 * ))
 * ```
 *
 * Java callers use the static factories: `PatchValue.set(value)`,
 * `PatchValue.clear()`, `PatchValue.omit()`.
 */
public sealed interface PatchValue<out T> {
    public data class Set<out T>(val value: T) : PatchValue<T>

    public object Clear : PatchValue<Nothing>

    public object NotIncluded : PatchValue<Nothing>

    public companion object {
        @JvmStatic
        public fun <T> set(value: T): PatchValue<T> = Set(value)

        @JvmStatic
        public fun clear(): PatchValue<Nothing> = Clear

        @JvmStatic
        public fun omit(): PatchValue<Nothing> = NotIncluded
    }
}
