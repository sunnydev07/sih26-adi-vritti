package in.adivritti.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RedisConfig {

    @Bean
    CacheManager cacheManager(@Value("${app.verification.cache-ttl-seconds:3600}") long ttlSeconds) {
        // Caffeine local cache fronts verification results; Redis backs shared state.
        // Per-claim TTL is enforced by key prefixing in VerificationOrchestrator.
        CaffeineCacheManager manager = new CaffeineCacheManager("verification", "dashboard");
        manager.setCacheSpecification("expireAfterWrite=" + ttlSeconds + "s,maximumSize=10000");
        return manager;
    }
}
