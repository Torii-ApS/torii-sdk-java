@file:JvmName("VerifyWebhook")

package so.torii.backend

// Webhook signature verification. torii's outbound webhook subsystem is not
// yet available; we ship a placeholder here so the public SDK surface is
// stable when it lands — adopting this signature won't be a breaking change
// for SDK users.

/**
 * Verified webhook event delivered by torii. Surface-shape only — the
 * underlying verification has not shipped yet.
 */
public data class WebhookEvent(
    val type: String,
    val id: String,
    val createdAt: String,
    val data: Map<String, Any?>,
)

/**
 * Verify a torii outbound webhook signature.
 *
 * Not yet implemented — torii's outbound webhook subsystem is not yet
 * available. This stub reserves the SDK surface so adopting it later doesn't
 * break callers.
 */
public fun verifyWebhook(
    @Suppress("UNUSED_PARAMETER") secret: String,
    @Suppress("UNUSED_PARAMETER") headers: Map<String, List<String>>,
    @Suppress("UNUSED_PARAMETER") payload: ByteArray,
): WebhookEvent {
    throw ToriiAuthException(
        "verifyWebhook: torii's outbound webhook subsystem is not yet available.",
    )
}
