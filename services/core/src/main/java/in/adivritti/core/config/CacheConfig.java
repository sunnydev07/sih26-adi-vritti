package in.adivritti.core.config;

import java.time.Duration;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Cache configuration.
 *
 * <p>Previously this class was called {@code RedisConfig} but returned a
 * {@code CaffeineCacheManager} — an in-process cache. Every instance therefore had a
 * different cache, so a verification one instance performed was invisible to the
 * next, and there was no shared state across a horizontally scaled deployment.
 *
 * <p>Now the caches are Redis-backed with per-cache TTLs (verification results live
 * far longer than dashboards), and a {@link CacheErrorHandler} treats a cache outage
 * as a miss rather than a 500 on the request path.
 */
@Configuration
public class CacheConfig implements CachingConfigurer {

    @Bean
    CacheManager cacheManager(RedisConnectionFactory connectionFactory,
        RedisCacheTtls ttls) {

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
            .disableCachingNullValues()
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(base.entryTtl(ttls.defaultTtl()))
            .withCacheConfiguration("verification",
                base.entryTtl(ttls.verification()))
            .withCacheConfiguration("dashboard", base.entryTtl(ttls.dashboard()))
            .transactionAware()
            .build();
        return manager;
    }

    /**
     * A Redis outage degrades to "always a cache miss" rather than failing the
     * request. Verification and dashboards must keep working when the cache is down.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log(cache, "get", e);
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key,
                Object value) {
                log(cache, "put", e);
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log(cache, "evict", e);
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log(cache, "clear", e);
            }

            private void log(Cache cache, String op, RuntimeException e) {
                org.slf4j.LoggerFactory
                    .getLogger(CacheConfig.class)
                    .warn("Cache {} failed on {}: {}", cache.getName(), op, e.toString());
            }
        };
    }

    /** Per-cache TTLs, bound once so the cache config stays declarative. */
    public record RedisCacheTtls(Duration verification, Duration dashboard, Duration defaultTtl) {
        public RedisCacheTtls {
            if (verification.isNegative() || verification.isZero()) {
                throw new IllegalArgumentException("verification TTL must be positive");
            }
            if (dashboard.isNegative() || dashboard.isZero()) {
                throw new IllegalArgumentException("dashboard TTL must be positive");
            }
        }
    }

    @Bean
    RedisCacheTtls cacheTtls(
        @org.springframework.beans.factory.annotation.Value(
            "${app.verification.cache-ttl-seconds:3600}") long verificationSeconds,
        @org.springframework.beans.factory.annotation.Value(
            "${app.verification.dashboard-cache-ttl-seconds:300}") long dashboardSeconds) {
        return new RedisCacheTtls(
            Duration.ofSeconds(verificationSeconds),
            Duration.ofSeconds(dashboardSeconds),
            Duration.ofMinutes(5));
    }
}
