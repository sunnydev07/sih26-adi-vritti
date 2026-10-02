package in.adivritti.core.eligibility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.repository.SchemeRuleVersionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Task 2.3: the rules cache is invalidated on a timer while evaluations read it.
 *
 * <p>It used to be a {@code ConcurrentHashMap} cleared in place, and readers did
 * {@code containsKey} then {@code get}. Both halves of that are racy:
 *
 * <ul>
 *   <li>a clear between the two lookups turns a cache hit into {@code null}, and a
 *       null rule list is the "rules are not published" fail-closed verdict — the
 *       student is told a scheme has no criteria for as long as the race lasts;
 *   <li>a load that started before the clear and finished after it re-inserts into
 *       the map the clear just emptied, so the edit the invalidation exists to pick
 *       up is masked for another interval.
 * </ul>
 *
 * <p>The cache is now an {@code AtomicReference} to an immutable map: reads take a
 * snapshot and invalidation is a single atomic swap. These tests run the two
 * operations against each other hard enough to hit the old code's window.
 */
class EligibilityRuleCacheConcurrencyTest {

    private static final String YEAR = "2026-27";
    private static final List<String> SCHEMES =
        List.of("pre-matric", "post-matric", "top-class", "nfst", "nos");

    /**
     * A rule whose only difference between the two versions is the {@code onFail}
     * text, so the reason string in the response proves which file version was
     * read. The student has no claims at all, so the rule fails and the reason
     * reaches the client.
     */
    private static String rulesDoc(String marker) {
        return """
            {"scheme": "PRE_MATRIC", "academic_year": "%s",
             "rules": [{"claim": "income", "op": "exists", "onFail": "%s"}]}
            """.formatted(YEAR, marker);
    }

    private static EligibilityService serviceFor(Path rulesRoot) {
        ClaimRepository claims = mock(ClaimRepository.class);
        when(claims.findLiveByUsidWithTypes(any(UUID.class))).thenReturn(List.of());
        // A real engine, not a mock: the point of the assertions is which rules
        // text ended up in the verdict, which a mocked engine would hide.
        return new EligibilityService(new RuleEngine(), claims,
            mock(ClaimValueCipher.class), mock(SchemeRuleVersionRepository.class),
            rulesRoot.toString());
    }

    private static void writeAll(Path root, String marker) throws Exception {
        Path year = root.resolve(YEAR);
        Files.createDirectories(year);
        for (String scheme : SCHEMES) {
            Files.writeString(year.resolve(scheme + ".json"), rulesDoc(marker));
        }
    }

    /** True when every scheme's verdict still carries the live file's marker. */
    private static boolean allVerdictsCarry(EligibilityService service, String marker) {
        return service.evaluate(new EligibilityRequest(UUID.randomUUID(), YEAR))
            .verdicts().stream()
            .allMatch(v -> v.reasons().stream().anyMatch(r -> r.contains(marker)));
    }

    @Test
    @DisplayName("a read racing an invalidation never sees a half-cleared cache")
    void readsAndInvalidationDoNotTear(@TempDir Path root) throws Exception {
        writeAll(root, "live");
        EligibilityService service = serviceFor(root);

        int threads = 8;
        int rounds = 300;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger evaluations = new AtomicInteger();

        // One thread invalidating, the rest evaluating: the exact overlap the
        // in-place clear could not survive.
        Runnable evaluate = () -> {
            try {
                start.await();
                for (int i = 0; i < rounds; i++) {
                    var verdicts = service.evaluate(
                        new EligibilityRequest(UUID.randomUUID(), YEAR)).verdicts();
                    if (verdicts.size() != SCHEMES.size()) {
                        throw new AssertionError("expected " + SCHEMES.size()
                            + " verdicts, got " + verdicts.size() + " at iteration " + i);
                    }
                    // Every scheme has a rule file on disk, so a verdict that
                    // claims the rules are unpublished means the read observed a
                    // torn cache and fell through to the fail-closed branch.
                    for (var v : verdicts) {
                        if (v.reasons().stream()
                            .anyMatch(r -> r.contains("not published")
                                || r.contains("currently unavailable"))) {
                            throw new AssertionError(
                                "torn cache read for " + v.scheme() + " at iteration " + i);
                        }
                    }
                    evaluations.incrementAndGet();
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };
        Runnable invalidate = () -> {
            try {
                start.await();
                for (int i = 0; i < rounds; i++) service.invalidateRuleCache();
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        };

        for (int i = 0; i < threads - 1; i++) pool.submit(evaluate);
        pool.submit(invalidate);
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(120, TimeUnit.SECONDS))
            .as("worker threads finished").isTrue();

        assertThat(failure.get()).isNull();
        assertThat(evaluations.get()).isEqualTo((threads - 1) * rounds);
    }

    @Test
    @DisplayName("an edit to a rules file is picked up after the next invalidation")
    void invalidationLetsTheNextEvaluationSeeTheEdit(@TempDir Path root) throws Exception {
        writeAll(root, "before");
        EligibilityService service = serviceFor(root);

        assertThat(allVerdictsCarry(service, "before"))
            .as("cache warmed from the pre-edit file").isTrue();

        writeAll(root, "after");
        // No invalidation yet: the cache is the point, so this must still be old.
        assertThat(allVerdictsCarry(service, "before"))
            .as("a warm cache still serves the version it loaded").isTrue();

        service.invalidateRuleCache();

        assertThat(allVerdictsCarry(service, "after"))
            .as("the edit is visible on the next evaluation").isTrue();
    }

    @Test
    @DisplayName("invalidating an already-empty cache is a no-op")
    void invalidatingAnEmptyCacheIsHarmless(@TempDir Path root) throws Exception {
        writeAll(root, "live");
        EligibilityService service = serviceFor(root);
        service.invalidateRuleCache();
        service.invalidateRuleCache();
        assertThat(allVerdictsCarry(service, "live")).isTrue();
    }
}
