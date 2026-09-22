package com.shopscale.security.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Counters live in this JVM only. Good enough for a single instance; with several replicas each one
 * counts separately, so the effective limit multiplies by the number of instances.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimiter implements RateLimiter {

    private final ConcurrentHashMap<String, AtomicLong> counters = new ConcurrentHashMap<>();

    @Override
    public long tryAcquire(String key, int limit, Duration window) {
        long windowId = System.currentTimeMillis() / window.toMillis();
        String bucket = key + ":" + windowId;
        if (counters.size() > 50_000) {
            counters.keySet().removeIf(k -> !k.endsWith(":" + windowId));
        }
        long hits = counters.computeIfAbsent(bucket, k -> new AtomicLong()).incrementAndGet();
        return limit - hits;
    }
}
