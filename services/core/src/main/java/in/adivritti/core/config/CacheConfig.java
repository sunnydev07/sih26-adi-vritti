package in.adivritti.core.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

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
                .fromSerializer(jsonSerializer()));

        RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(base.entryTtl(ttls.defaultTtl()))
            .withCacheConfiguration("verification",
                base.entryTtl(ttls.verification()))
            // No "dashboard" entry: no @Cacheable("dashboard") exists anywhere,
            // and a pre-configured TTL here would silently become a cross-scholar
            // key-collision bug the day someone wires it without scholar-scoped
            // keys. Add the cache AND its key design together, not ahead.
            .transactionAware()
            .build();
        return manager;
    }

    /**
     * Jackson serializer for cache values.
     *
     * <p>{@code new GenericJackson2JsonRedisSerializer()} builds a bare
     * {@code ObjectMapper}: it never calls {@code findAndRegisterModules()}, so Java
     * time types have no serializer at all. Caching a {@code VerifyResponse} — which
     * carries {@code validUntil} and {@code attemptedAt} — therefore threw on every
     * put, the {@link CacheErrorHandler} below classified it as a cache outage and
     * logged a WARN, and the verification cache was a permanent miss that looked
     * healthy. Registering {@link JavaTimeModule}, and writing ISO-8601 rather than
     * epoch timestamps (the contract's date convention), fixes the put.
     *
     * <p>Default typing is kept because cache values are held as {@code Object}:
     * without a type hint the read hands back a {@code LinkedHashMap} and the caller
     * dies on a cast. It is narrow, though — only the packages that can appear in
     * cached values — so a tampered Redis entry cannot name an arbitrary class for
     * Jackson to instantiate.
     */
    static GenericJackson2JsonRedisSerializer jsonSerializer() {
        ObjectMapper mapper = Jackson2ObjectMapperBuilder.json()
            .modules(new JavaTimeModule())
            // ISO-8601 on the wire, and do not silently re-zone what is read back:
            // the default UTC adjustment made a cache hit render a different offset
            // (+05:30 became Z) from a fresh, uncached read of the same instant.
            .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS,
                DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();
        mapper.activateDefaultTyping(
            BasicPolymorphicTypeValidator.builder()
                .allowIfSubType("in.adivritti.")
                .allowIfSubType("java.util.")
                .allowIfSubType("java.time.")
                .allowIfSubType("java.lang.")
                .allowIfSubType("java.math.")
                .build(),
            ObjectMapper.DefaultTyping.EVERYTHING,
            JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(mapper, null);
        return new GenericJackson2JsonRedisSerializer(mapper);
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
    public record RedisCacheTtls(Duration verification, Duration defaultTtl) {
        public RedisCacheTtls {
            if (verification.isNegative() || verification.isZero()) {
                throw new IllegalArgumentException("verification TTL must be positive");
            }
        }
    }

    @Bean
    RedisCacheTtls cacheTtls(
        @org.springframework.beans.factory.annotation.Value(
            "${app.verification.cache-ttl-seconds:3600}") long verificationSeconds) {
        return new RedisCacheTtls(
            Duration.ofSeconds(verificationSeconds),
            Duration.ofMinutes(5));
    }
}
