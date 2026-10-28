package dev.openmacro.core.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InvocationRateLimiterTest {

    @Test
    fun windowSlides() {
        var now = 0L
        val limiter = InvocationRateLimiter(maxPerWindow = 2, windowMillis = 1_000, clock = { now })
        assertTrue(limiter.tryAcquire(1))
        assertTrue(limiter.tryAcquire(1))
        assertFalse(limiter.tryAcquire(1))
        assertTrue("other macros are independent", limiter.tryAcquire(2))
        now = 1_000
        assertTrue(limiter.tryAcquire(1))
    }
}
