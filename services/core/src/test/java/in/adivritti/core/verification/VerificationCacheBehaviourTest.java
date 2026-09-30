package in.adivritti.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationAttempt;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Cache semantics of {@link VerificationOrchestrator#verify}.
 *
 * <p>The key used to be {@code usid + claimType}, so a second submission for the same
 * claim backed by a <em>different</em> document was answered from the first document's
 * cached verdict for the whole TTL and never got its own wallet entry. The key now
 * carries the evidence reference, which makes a hit mean "this exact verification
 * already ran and its claim is persisted" -- the only case where skipping the write is
 * safe.
 *
 * <p>This runs on a {@link ConcurrentMapCacheManager}, deliberately: it pins the key
 * expression and the {@code unless} clause without needing Redis.
 * {@code CacheConfigSerializationTest} covers the Redis wire format separately.
 */
@SpringJUnitConfig(classes = VerificationCacheBehaviourTest.CachingContext.class)
class VerificationCacheBehaviourTest {

    private static final UUID USID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER_USID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Configuration(proxyBeanMethods = false)
    @EnableCaching
    static class CachingContext {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("verification");
        }

        @Bean
        ClaimValueCipher claimValueCipher() {
            ClaimValueCipher cipher = mock(ClaimValueCipher.class);
            when(cipher.seal(anyString())).thenReturn(new byte[28]);
            return cipher;
        }

        @Bean
        ClaimRepository claimRepository() {
            ClaimRepository repository = mock(ClaimRepository.class);
            when(repository.save(any(Claim.class))).thenAnswer(invocation -> {
                Claim claim = invocation.getArgument(0);
                if (claim.id == null) {
                    claim.id = UUID.randomUUID();
                }
                return claim;
            });
            // Keyed requests persist through saveAndFlush (so a unique-violation
            // race can be caught and resolved to the winner); same stub shape.
            when(repository.saveAndFlush(any(Claim.class))).thenAnswer(invocation -> {
                Claim claim = invocation.getArgument(0);
                if (claim.id == null) {
                    claim.id = UUID.randomUUID();
                }
                return claim;
            });
            // No prior keyed claims in this suite: the cache behaviour under test
            // must not be short-circuited by the idempotency replay.
            when(repository.findFirstByUsidAndIdempotencyKey(any(UUID.class), anyString()))
                .thenReturn(Optional.empty());
            return repository;
        }

        @Bean
        DeficiencyRepository deficiencyRepository() {
            return mock(DeficiencyRepository.class);
        }

        @Bean
        GrantingStrategy grantingStrategy() {
            return new GrantingStrategy();
        }

        @Bean
        VerificationOrchestrator verificationOrchestrator(List<VerificationStrategy> chain,
            ClaimRepository claims, DeficiencyRepository deficiencies, ClaimValueCipher cipher) {
            return new VerificationOrchestrator(chain, claims, deficiencies, cipher);
        }
    }

    /** Always-deciding strategy that counts how often the orchestrator actually ran. */
    static final class GrantingStrategy implements VerificationStrategy {

        final AtomicInteger calls = new AtomicInteger();
        volatile boolean verified = true;

        @Override
        public String tier() {
            return "gov_verified";
        }

        @Override
        public Optional<TierResult> attempt(VerificationAttempt attempt) {
            calls.incrementAndGet();
            return Optional.of(new TierResult(verified, verified ? 0.99 : 0.0,
                "DigiLocker", "api_setu", null, null));
        }
    }

    @Autowired
    private VerificationOrchestrator orchestrator;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private GrantingStrategy strategy;

    /**
     * The strategy is a singleton shared by every method in this class, so a test that
     * flips {@code verified} (or leaves an entry in the cache) would otherwise decide
     * the outcome of whichever test runs next -- JUnit's default order is not source
     * order. Reset both before each method.
     */
    @BeforeEach
    void resetStrategyAndCache() {
        strategy.verified = true;
        strategy.calls.set(0);
        cacheManager.getCache("verification").clear();
    }

    @Test
    @DisplayName("replaying the same evidence is served from the cache without re-probing")
    void identicalEvidenceHitsTheCache() {
        VerifyRequest request = new VerifyRequest(USID, "income", "digilocker/doc-1", "idem-1");

        VerifyResponse first = orchestrator.verify(request);
        strategy.calls.set(0);
        VerifyResponse replay = orchestrator.verify(request);

        assertThat(strategy.calls)
            .as("proxy=%s class=%s cache=%s",
                org.springframework.aop.support.AopUtils.isAopProxy(orchestrator),
                orchestrator.getClass().getName(),
                cacheManager.getCache("verification").getNativeCache())
            .hasValue(0);
        assertThat(replay).isEqualTo(first);
    }

    @Test
    @DisplayName("the same claim backed by different evidence is verified again")
    void differentEvidenceIsNotServedFromTheCache() {
        orchestrator.verify(new VerifyRequest(USID, "income", "digilocker/doc-1", "idem-1"));
        strategy.calls.set(0);

        orchestrator.verify(new VerifyRequest(USID, "income", "digilocker/doc-2", "idem-2"));

        assertThat(strategy.calls).hasValue(1);
    }

    @Test
    @DisplayName("the cache entry is scoped to the scholar")
    void theCacheIsScopedToTheUsid() {
        orchestrator.verify(new VerifyRequest(USID, "income", "digilocker/doc-1", null));
        strategy.calls.set(0);

        orchestrator.verify(new VerifyRequest(OTHER_USID, "income", "digilocker/doc-1", null));

        assertThat(strategy.calls).hasValue(1);
    }

    @Test
    @DisplayName("a failed verdict is transient and is never cached")
    void failedVerdictsAreNotCached() {
        strategy.verified = false;
        VerifyRequest request = new VerifyRequest(USID, "income", "digilocker/doc-3", null);

        assertThat(orchestrator.verify(request).verdict()).isEqualTo("failed");
        strategy.calls.set(0);
        orchestrator.verify(request);

        assertThat(strategy.calls).hasValue(1);
    }
}
