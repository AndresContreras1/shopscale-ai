package com.shopscale.security.ratelimit;

import java.time.Duration;

/**
 * Fixed-window rate limiter: at most {@code limit} hits per key inside each window.
 */
public interface RateLimiter {

    /**
     * Registers a hit for the key.
     *
     * @return the hits still allowed in the current window, or a negative number when the limit is exceeded
     */
    long tryAcquire(String key, int limit, Duration window);
}
