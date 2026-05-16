@file:JvmName("VerifyWebhook")

package so.torii.backend

// Webhook signature verification. torii's outbound webhook subsystem is
// being designed under #424 Phase 0.5; we ship a placeholder here so the
// public SDK surface is stable when webhooks land — adopting this signature
// won't be a breaking change for SDK users.
//
// Once 0.5 lands with the final signing scheme (Svix-compatible HMAC or
// homegrown), this function becomes the real verifier.

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
 * Not yet implemented — torii's outbound webhook subsystem has not shipped
 * (#424 Phase 0.5). This stub keeps the SDK surface stable so adopting it
 * later doesn't break callers.
 */
public fun verifyWebhook(
    @Suppress("UNUSED_PARAMETER") secret: String,
    @Suppress("UNUSED_PARAMETER") headers: Map<String, List<String>>,
    @Suppress("UNUSED_PARAMETER") payload: ByteArray,
): WebhookEvent {
    throw ToriiAuthException(
        "verifyWebhook: torii's outbound webhook subsystem has not shipped yet — see #424 Phase 0.5",
    )
}
