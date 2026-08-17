package com.applovin.mediation.adapters

/**
 * Coalesces concurrent SDK-init attempts so only one caller performs the actual
 * initialization while every other caller waits for its outcome.
 *
 * Main-thread-confined: AppLovin MAX delivers all adapter entry points on the main
 * thread and the Velocity SDK delivers init callbacks there too, so no locking is
 * required — matching the `Handler(Looper.getMainLooper())` pattern already used by
 * [VelocityAdsMediationAdapter].
 */
internal class InitCoalescer<Outcome> {
    /** `true` while a claimed init attempt is in flight. */
    var isClaimed: Boolean = false
        private set

    private val pendingHandlers = mutableListOf<(Outcome) -> Unit>()

    /**
     * Registers [handler] to receive the outcome of the in-flight (or about to
     * start) init attempt.
     *
     * @return `true` if the caller won the claim and must perform the init, then
     *   broadcast via [complete]; `false` if the handler was parked and will be
     *   invoked when the winner broadcasts.
     */
    fun claim(handler: (Outcome) -> Unit): Boolean {
        pendingHandlers.add(handler)
        if (isClaimed) {
            return false
        }
        isClaimed = true
        return true
    }

    /**
     * Broadcasts [outcome] to every registered handler (winner included, in
     * registration order) and resets the claim so a later attempt can run —
     * e.g. a re-init after a transient failure.
     */
    fun complete(outcome: Outcome) {
        val handlers = pendingHandlers.toList()
        pendingHandlers.clear()
        isClaimed = false
        for (handler in handlers) {
            handler(outcome)
        }
    }
}
