package in.adivritti.core.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationAttempt;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import in.adivritti.core.verification.strategy.VerificationStrategy.TierResult;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Task 2.1: the wallet must hold exactly one row per (usid, idempotency key) even
 * when several attempts with the same key are in flight together, and the loser
 * must not turn a successful verification into a 500.
 *
 * <p>The unique index is there ({@code uq_claim_idempotency}, migration V2). What
 * was wrong was the recovery: the losing attempt caught
 * {@code DataIntegrityViolationException} and re-read the winner's row
 * <em>inside the same transaction</em>. On PostgreSQL a constraint violation
 * aborts the transaction, so the next statement fails with "current transaction is
 * aborted" and the caller gets a 500 for a request that had already succeeded on
 * another node. The violation now rolls the transaction back and is retried
 * outside it, where the idempotency pre-check finds the committed winner.
 */
class VerificationIdempotencyRaceTest {

    private static final String KEY = "retry-abc-123";

    /**
     * In-memory wallet that behaves like PostgreSQL on a unique-index collision:
     * the statement fails and the thread's transaction is dead, so the next
     * statement on it throws until the transaction ends.
     */
    private static final class PostgresLikeWallet {
        private final Map<String, Claim> rows = new ConcurrentHashMap<>();
        private final Map<Thread, Boolean> aborted = new ConcurrentHashMap<>();
        private final AtomicInteger preCheckReads = new AtomicInteger();
        private final AtomicInteger postViolationReads = new AtomicInteger();
        private final AtomicInteger lostRaces = new AtomicInteger();

        /** A fresh transaction on this thread: the abort is no longer in scope. */
        void beginTransaction() {
            aborted.remove(Thread.currentThread());
        }

        void endTransaction() {
            aborted.remove(Thread.currentThread());
        }

        int rowCount() {
            return rows.size();
        }

        /** Attempts that hit the unique index and lost. */
        int lostRaces() {
            return lostRaces.get();
        }

        int preCheckReads() {
            return preCheckReads.get();
        }

        int readsAttemptedOnAnAbortedTransaction() {
            return postViolationReads.get();
        }

        Claim save(Claim c) {
            checkLive();
            if (c.id == null) c.id = UUID.randomUUID();
            if (c.verifiedAt == null) c.verifiedAt = ZonedDateTime.now();
            if (c.idempotencyKey != null) {
                String key = c.usid + "/" + c.idempotencyKey;
                if (rows.putIfAbsent(key, c) != null) {
                    aborted.put(Thread.currentThread(), true);
                    lostRaces.incrementAndGet();
                    throw new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint "
                            + "\"uq_claim_idempotency\"");
                }
            }
            return c;
        }

        Optional<Claim> find(UUID usid, String key) {
            if (aborted.getOrDefault(Thread.currentThread(), false)) {
                postViolationReads.incrementAndGet();
                throw new IllegalStateException(
                    "current transaction is aborted, commands ignored until end of"
                        + " transaction block");
            }
            preCheckReads.incrementAndGet();
            return Optional.ofNullable(rows.get(usid + "/" + key));
        }

        private void checkLive() {
            if (aborted.getOrDefault(Thread.currentThread(), false)) {
                throw new IllegalStateException(
                    "current transaction is aborted, commands ignored until end of"
                        + " transaction block");
            }
        }
    }

    /** Wires the fake wallet into the repository interface the code depends on. */
    private static ClaimRepository repository(PostgresLikeWallet wallet) {
        ClaimRepository repo = mock(ClaimRepository.class);
        when(repo.save(any(Claim.class))).thenAnswer(i -> wallet.save(i.getArgument(0)));
        when(repo.saveAndFlush(any(Claim.class))).thenAnswer(i -> wallet.save(i.getArgument(0)));
        when(repo.findFirstByUsidAndIdempotencyKey(any(UUID.class), any(String.class)))
            .thenAnswer(i -> wallet.find(i.getArgument(0), i.getArgument(1)));
        return repo;
    }

    private static ClaimValueCipher cipher() {
        ClaimValueCipher c = mock(ClaimValueCipher.class);
        when(c.seal(any(String.class))).thenReturn(new byte[] {1, 2, 3});
        return c;
    }

    private static VerificationStrategy verifying(long delayMillis, AtomicInteger runs) {
        return new VerificationStrategy() {
            @Override
            public String tier() {
                return "gov_verified";
            }

            @Override
            public Optional<TierResult> attempt(VerificationAttempt attempt) {
                runs.incrementAndGet();
                // Widen the window between the idempotency pre-check and the
                // wallet write: the unique index only matters when two attempts
                // are genuinely in that gap at the same time.
                sleep(delayMillis);
                return Optional.of(new TierResult(true, 0.95, "nsp", "api", "ok", "250000"));
            }
        };
    }

    @Test
    @DisplayName("concurrent attempts with one key produce exactly one wallet row")
    void concurrentSameKeyProducesOneClaim() throws Exception {
        var wallet = new PostgresLikeWallet();
        AtomicInteger strategyRuns = new AtomicInteger();
        int threads = 8;
        // Hold every attempt inside the strategy until all of them have passed the
        // idempotency pre-check, so the collision is the scenario under test and
        // not a scheduling accident. The timeout keeps a short-circuiting thread
        // from deadlocking the pool.
        var allInside = new java.util.concurrent.CyclicBarrier(threads);
        VerificationStrategy strategy = new VerificationStrategy() {
            @Override
            public String tier() {
                return "gov_verified";
            }

            @Override
            public Optional<TierResult> attempt(VerificationAttempt attempt) {
                strategyRuns.incrementAndGet();
                try {
                    allInside.await(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new IllegalStateException("barrier released early", e);
                }
                return Optional.of(new TierResult(true, 0.95, "nsp", "api", "ok", "250000"));
            }
        };

        // The Spring transaction proxy begins a fresh transaction on *every*
        // call to the proxied method, including the orchestrator's retry after a
        // rollback. The spy stands in for that proxy: opening a transaction here
        // is what clears a previous abort, exactly as a new tx would.
        var real = new VerificationTransaction(List.of(strategy), repository(wallet),
            mock(DeficiencyRepository.class), cipher());
        var proxied = org.mockito.Mockito.spy(real);
        org.mockito.Mockito.doAnswer(invocation -> {
            wallet.beginTransaction();
            return invocation.callRealMethod();
        }).when(proxied).run(any());

        VerificationOrchestrator orchestrator = new VerificationOrchestrator(proxied);

        UUID usid = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<UUID> claimIds = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    wallet.beginTransaction();
                    var response = orchestrator.verify(
                        new VerifyRequest(usid, "income", "ev-1", KEY));
                    wallet.endTransaction();
                    claimIds.add(response.claimId());
                } catch (Throwable t) {
                    failure.compareAndSet(null, t);
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();

        assertThat(failure.get())
            .as("no attempt may surface a constraint violation to the caller")
            .isNull();
        assertThat(wallet.rowCount()).as("one row per (usid, idempotency key)").isEqualTo(1);
        assertThat(claimIds).hasSize(threads);
        assertThat(claimIds).as("every caller gets the same claim id")
            .containsOnly(claimIds.get(0));
        assertThat(strategyRuns.get())
            .as("all eight attempts reached the wallet write, so the race really happened")
            .isEqualTo(threads);
        assertThat(wallet.lostRaces())
            .as("seven of them lost the unique index and replayed the winner")
            .isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("a unique-key violation aborts the transaction instead of reading it back")
    void violationAbortsRatherThanReadsBackInTheAbortedTransaction() {
        // The old recovery path: catch the violation, then SELECT the winner in
        // the same transaction. On PostgreSQL that SELECT raises "current
        // transaction is aborted". This test pins the replacement: the violation
        // leaves run() immediately so the transaction rolls back.
        var wallet = new PostgresLikeWallet();
        ClaimRepository repo = repository(wallet);
        Claim winner = new Claim();
        winner.id = UUID.randomUUID();
        winner.usid = UUID.randomUUID();
        winner.claimType = "income";
        winner.source = "gov_verified";
        winner.method = "gov_verified";
        winner.confidence = 0.95;
        winner.validUntil = ZonedDateTime.now().plusDays(365);
        winner.idempotencyKey = KEY;
        winner.valueEncrypted = new byte[] {1};
        wallet.rows.put(winner.usid + "/" + KEY, winner);

        var service = new VerificationTransaction(
            List.of(verifying(0, new AtomicInteger())), repo,
            mock(DeficiencyRepository.class), cipher());

        // The pre-check is bypassed to force the write to collide: simulate the
        // row appearing between the pre-check and the INSERT.
        when(repo.findFirstByUsidAndIdempotencyKey(any(UUID.class), any(String.class)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.run(
            new VerifyRequest(winner.usid, "income", "ev-1", KEY)))
            .isInstanceOf(VerificationTransaction.ClaimKeyRaceException.class)
            .hasMessageContaining(KEY);

        assertThat(wallet.readsAttemptedOnAnAbortedTransaction())
            .as("no statement may be issued on the aborted transaction")
            .isZero();
    }

    @Test
    @DisplayName("a lost race is retried outside the transaction and returns the winner")
    void lostRaceIsRetriedAndReplaysTheWinner() {
        UUID usid = UUID.randomUUID();
        UUID winnerId = UUID.randomUUID();
        VerifyRequest req = new VerifyRequest(usid, "income", "ev-1", KEY);
        var winner = new VerifyResponse(winnerId, usid, "income", "verified", 0.95,
            ZonedDateTime.now().plusDays(365), List.of(), null);

        VerificationTransaction transaction = mock(VerificationTransaction.class);
        when(transaction.run(any()))
            .thenThrow(new VerificationTransaction.ClaimKeyRaceException(
                KEY, new DataIntegrityViolationException("uq_claim_idempotency")))
            .thenReturn(winner);

        var response = new VerificationOrchestrator(transaction).verify(req);

        assertThat(response.claimId()).isEqualTo(winnerId);
        verify(transaction, times(2)).run(req);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
