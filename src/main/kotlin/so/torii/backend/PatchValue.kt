package so.torii.backend

/**
 * Three-state value for PATCH semantics on nullable fields:
 *  - [Set] sends a concrete value (including `null` to clear)
 *  - [NotIncluded] omits the field from the request — server keeps existing
 *
 * Use it so callers can distinguish "leave alone" from "set to null".
 *
 * ```kotlin
 * client.users.update(id, UserPatch(
 *     name = PatchValue.Set("Ada"),
 *     phone = PatchValue.Set(null), // clears phone
 *     // address omitted -> NotIncluded -> server-side untouched
 * ))
 * ```
 *
 * Java callers can use the static factories: `PatchValue.set(value)` and
 * `PatchValue.notIncluded()`.
 */
public sealed class PatchValue<out T> {
    public data class Set<out T>(val value: T) : PatchValue<T>()

    public object NotIncluded : PatchValue<Nothing>()

    public companion object {
        @JvmStatic
        public fun <T> set(value: T): PatchValue<T> = Set(value)

        @JvmStatic
        public fun <T> notIncluded(): PatchValue<T> = @Suppress("UNCHECKED_CAST") (NotIncluded as PatchValue<T>)
    }
}
