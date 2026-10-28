package dev.openmacro.core.engine

/**
 * Loop guard: allows at most [maxPerWindow] invocations of one macro within [windowMillis].
 * Protects against feedback loops such as a macro whose action re-fires its own trigger.
 */
class InvocationRateLimiter(
    private val maxPerWindow: Int = 10,
    private val windowMillis: Long = 1_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val history = HashMap<Long, ArrayDeque<Long>>()

    @Synchronized
    fun tryAcquire(macroId: Long): Boolean {
        val now = clock()
        val stamps = history.getOrPut(macroId) { ArrayDeque() }
        while (stamps.isNotEmpty() && now - stamps.first() >= windowMillis) stamps.removeFirst()
        if (stamps.size >= maxPerWindow) return false
        stamps.addLast(now)
        return true
    }
}
