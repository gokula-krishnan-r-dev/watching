package com.meritscreen.core.common.messaging

/**
 * One handler per FCM data-message `type`. Registered via Hilt multibinding
 * (`@Binds @IntoSet`) from any feature module so `:core:firebase`'s messaging service never
 * needs to depend on `:features:child` / `:features:devices` directly.
 *
 * Implementations must be fast and non-blocking (FCM gives ~20s total before the OS may
 * kill the process) — the contract is to *enqueue* a bounded WorkManager job and return,
 * never to perform network I/O inline on the calling thread.
 */
interface FcmMessageHandler {
    /** @return true if this handler recognized `data["type"]` and enqueued follow-up work. */
    fun handle(data: Map<String, String>): Boolean
}

/**
 * Registered via Hilt multibinding so a fresh FCM token (from `onNewToken`, or read once at
 * startup in case it changed while the process was dead) is pushed to the right Firestore
 * document by whichever module owns that identity (child device vs parent user).
 */
interface FcmTokenRegistrar {
    /** Fast, non-blocking: enqueue a bounded WorkManager job, never call the network inline. */
    fun onTokenRefreshed(token: String)
}
