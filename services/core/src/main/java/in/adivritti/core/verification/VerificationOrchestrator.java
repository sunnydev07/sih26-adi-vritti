package in.adivritti.core.verification;

import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Tiered orchestrator: gov-verified -&gt; corroborated -&gt; assisted -&gt; pending-review.
 * Rules: a failure NEVER blocks the applicant (creates a deficiency instead);
 * every attempt writes an immutable provenance record (returned here, persisted by caller).
 *
 * <p>This bean is the cached, non-transactional front door; the work happens in
 * {@link VerificationTransaction}. That split is load-bearing — see its javadoc for
 * why {@code @Cacheable} and {@code @Transactional} cannot share a method here.
 */
@Service
public class VerificationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(VerificationOrchestrator.class);

    private final VerificationTransaction transaction;

    public VerificationOrchestrator(VerificationTransaction transaction) {
        this.transaction = transaction;
    }

    /**
     * Constructor for unit tests that wire the collaborators directly, without a
     * Spring context. Production always injects the {@link VerificationTransaction}
     * bean, which is what makes its {@code @Transactional} proxy apply.
     */
    public VerificationOrchestrator(List<VerificationStrategy> chain, ClaimRepository claims,
        DeficiencyRepository deficiencies, ClaimValueCipher cipher) {
        this(new VerificationTransaction(chain, claims, deficiencies, cipher));
    }

    /**
     * Only successful verifications are cached. A {@code failed} or
     * {@code pending_review} outcome is a transient state that the next attempt
     * should be able to improve on, so caching it for an hour would strand the
     * applicant in a stale verdict.
     *
     * <p>The key carries {@code evidenceRef}, not just {@code usid + claimType}: two
     * submissions for the same claim backed by different documents are different
     * verifications, and the two-part key served the first document's verdict for the
     * second (and skipped persisting its claim) for the whole TTL. With evidence in
     * the key, a cache hit means this exact verification already ran and its claim is
     * already in the wallet — which is what makes skipping the write on a hit safe.
     *
     * <p>The contract's {@code idempotencyKey} is honoured on top of the cache: a key
     * that already produced a claim for this scholar and claim type short-circuits
     * to a replay of that claim (same claim id, verdict, confidence, validity; empty provenance
     * trail, because the replay performed no verification). Unlike the cache, the
     * key survives across different evidence refs. Only successful verifications
     * deduplicate — a failure persists a fresh deficiency per attempt by design.
     */
    @Cacheable(
        value = "verification",
        key = "T(String).valueOf(#req.usid()) + ':' + T(String).valueOf(#req.claimType())"
            + " + ':' + T(String).valueOf(#req.evidenceRef())",
        unless = "#result == null || #result.verdict() == 'failed' || #result.verdict() == 'pending_review'")
    public VerifyResponse verify(VerifyRequest req) {
        try {
            return transaction.run(req);
        } catch (VerificationTransaction.ClaimKeyRaceException race) {
            // A concurrent attempt with the same key committed first. The
            // transaction that lost has rolled back, so nothing of it is
            // visible; the retry starts a fresh transaction whose idempotency
            // pre-check finds the winner's row and replays it. One wallet row
            // per (usid, claim type, key), whichever attempt got there first.
            log.info("Replaying the winning claim for a lost idempotency race: {}",
                race.getMessage());
            return transaction.run(req);
        }
    }

    /** Marker for a claim whose value the verification endpoint never received. */
    public static final String VALUELESS = VerificationTransaction.VALUELESS;
}
