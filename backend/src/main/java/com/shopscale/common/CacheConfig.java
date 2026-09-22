package com.shopscale.common;

import java.time.Duration;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

/**
 * What is cached and for how long. Only data that is read far more often than it changes:
 * <ul>
 *   <li>categories: change almost never;</li>
 *   <li>dashboard: expensive aggregations, a minute of staleness is fine;</li>
 *   <li>AI reports: each one costs money and seconds, so identical requests reuse the last report.</li>
 * </ul>
 * Stock is never cached: it must always be exact.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String CATEGORIES = "categories";
    public static final String DASHBOARD = "dashboard";
    public static final String AI_REPORTS = "aiReports";

    /** Applied only when CACHE_TYPE=redis (Docker Compose): one cache shared by every API replica. */
    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheTtls() {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith("shopscale:")
                .disableCachingNullValues();
        return builder -> builder
                .withCacheConfiguration(CATEGORIES, base.entryTtl(Duration.ofHours(1)))
                .withCacheConfiguration(DASHBOARD, base.entryTtl(Duration.ofSeconds(60)))
                .withCacheConfiguration(AI_REPORTS, base.entryTtl(Duration.ofMinutes(10)));
    }
}
