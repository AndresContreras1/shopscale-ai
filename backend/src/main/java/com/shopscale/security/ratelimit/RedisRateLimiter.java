package com.shopscale.security.ratelimit;

import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Counters live in Redis, so the limit holds across all API replicas behind the load balancer.
 * INCR is atomic in Redis: concurrent requests on different instances never lose a hit.
 */
@Component
@ConditionalOnProperty(name = "app.security.rate-limit.store", havingValue = "redis")
public class RedisRateLimiter implements RateLimiter {

    private final StringRedisTemplate redis;

    public RedisRateLimiter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long tryAcquire(String key, int limit, Duration window) {
        long windowId = System.currentTimeMillis() / window.toMillis();
        String bucket = "shopscale:" + key + ":" + windowId;
        Long hits = redis.opsForValue().increment(bucket);
        if (hits != null && hits == 1) {
            redis.expire(bucket, window.plusSeconds(1));
        }
        return limit - (hits == null ? 0 : hits);
    }
}
