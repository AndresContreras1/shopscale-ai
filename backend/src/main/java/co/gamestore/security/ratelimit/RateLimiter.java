package co.gamestore.security.ratelimit;

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

    /**
     * Reads the budget left for the key without spending any of it. Needed where a hit is only
     * registered on failure, such as counting wrong passwords per account.
     */
    long remaining(String key, int limit, Duration window);
}
