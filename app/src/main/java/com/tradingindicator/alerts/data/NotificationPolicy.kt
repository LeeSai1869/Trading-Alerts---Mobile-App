package com.tradingindicator.alerts.data

object NotificationPolicy {
    const val MAX_SIGNAL_AGE_MS = 60 * 1000L

    // History is retained separately; only the audible/visible push alert expires.
    fun isFresh(item: SignalMessage, now: Long): Boolean =
        item.receivedAt > 0 && item.receivedAt >= now - MAX_SIGNAL_AGE_MS
}
