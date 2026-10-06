package in.adivritti.core.verification;

import in.adivritti.core.claims.entity.Claim;
import in.adivritti.core.claims.repository.ClaimRepository;
import in.adivritti.core.common.util.ClaimValueCipher;
import in.adivritti.core.verification.dto.DeficiencyDtoHelper;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse.ProvenanceRecord;
import in.adivritti.core.verification.entity.Deficiency;
import in.adivritti.core.verification.repository.DeficiencyRepository;
import in.adivritti.core.verification.strategy.VerificationAttempt;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of a verification attempt, deliberately a separate bean
 * from {@link VerificationOrchestrator}.
 *
 * <p>The reason is proxy boundaries. {@code @Cacheable} and {@code @Transactional}
 * on the same method have no defined order relative to each other — both advisors
 * register at {@code LOWEST_PRECEDENCE} — so whether the cache entry is published
 * before or after the database commit depends on registration order, not on
 * anything the code says. When the cache wins the race it publishes a verdict
 * whose claim row is not committed yet: a concurrent reader on another node gets
 * the cached response, follows the claim id, and finds nothing. And a rollback
 * after a successful return leaves the entry behind, so a failed verification
 * serves as a success for the whole TTL.
 *
 * <p>Splitting the transaction into its own bean makes the ordering explicit: the
 * orchestrator's cached method is not transactional, it calls a method that is, and
 * Spring's transaction proxy commits on the way back out — before the cache
 * interceptor ever sees the return value.
 */
@Service
public class VerificationTransaction {

    private static final Logger log = LoggerFactory.getLogger(VerificationTransaction.class);

    static final String VERDICT_VERIFIED = "verified";
    static final String VERDICT_FAILED = "failed";
    static final String VERDICT_PENDING = "pending_review";
    static final int CLAIM_VALIDITY_DAYS = 365;

    private final List<VerificationStrategy> chain;
    private final ClaimRepository claims;
    private final DeficiencyRepository deficiencies;
    private final ClaimValueCipher cipher;

    public VerificationTransaction(List<VerificationStrategy> chain, ClaimRepository claims,
        DeficiencyRepository deficiencies, ClaimValueCipher cipher) {
        this.chain = chain.stream()
            .sorted((a, b) -> Integer.compare(tierOrder(a.tier()), tierOrder(b.tier())))
            .toList();
        this.claims = claims;
        this.deficiencies = deficiencies;
        this.cipher = cipher;
    }

    static int tierOrder(String tier) {
        return switch (tier) {
            case "gov_verified" -> 0;
            case "corroborated" -> 1;
            case "assisted" -> 2;
            default -> 3;
        };
    }

    /**
     * Raised when two attempts with the same idempotency key for the same scholar
     * and claim type reach the wallet write together and the database's unique
     * index picks a winner.
     *
     * <p>It propagates out of the transaction on purpose. The recovery the old
     * code attempted — catch the violation, then re-read the winner's row in the
     * same transaction — cannot work: on PostgreSQL a constraint violation
     * aborts the enclosing transaction, so the very next statement fails with
     * "current transaction is aborted" and the caller gets a 500 instead of the
     * claim that was created a millisecond earlier. Rolling back and retrying
     * outside is both correct and cheap, because the retry short-circuits on the
     * idempotency pre-check before any adapter is called.
     */
    static class ClaimKeyRaceException extends RuntimeException {
        ClaimKeyRaceException(String key, DataIntegrityViolationException cause) {
            super("idempotency key '" + key + "' was claimed by a concurrent verification", cause);
        }
    }

    @Transactional
    public VerifyResponse run(VerifyRequest req) {
        if (req == null) throw new IllegalArgumentException("Verify request is required");
        if (req.usid() == null) throw new IllegalArgumentException("usid is required");
        if (req.claimType() == null || req.claimType().isBlank()) {
            throw new IllegalArgumentException("claimType is required");
        }
        String idempotencyKey = normalizedKey(req.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<Claim> prior = claims.findFirstByUsidAndClaimTypeAndIdempotencyKey(
                req.usid(), req.claimType(), idempotencyKey);
            if (prior.isPresent()) {
                return replayResponse(prior.get());
            }
        }

        VerificationAttempt attempt = new VerificationAttempt(req);
        List<ProvenanceRecord> trail = new ArrayList<>();

        for (VerificationStrategy strategy : chain) {
            var outcome = strategy.attempt(attempt);
            if (outcome.isEmpty()) {
                trail.add(new ProvenanceRecord(strategy.tier(), "skip", "n/a",
                    ZonedDateTime.now(), 0.0));
                continue;
            }
            var r = outcome.get();
            trail.add(new ProvenanceRecord(strategy.tier(), r.method(), r.source(),
                ZonedDateTime.now(), r.confidence()));

            if (r.verified()) {
                ZonedDateTime validUntil = ZonedDateTime.now().plusDays(CLAIM_VALIDITY_DAYS);
                Claim claim = persistClaimIdempotent(req, strategy.tier(), r.confidence(),
                    validUntil, r.value());
                String verdict = strategy.tier().equals("gov_verified")
                    ? VERDICT_VERIFIED : strategy.tier();
                return new VerifyResponse(claim.id, req.usid(), req.claimType(), verdict,
                    r.confidence(), validUntil, trail, null);
            }

            if (strategy.tier().equals(VERDICT_PENDING)) {
                VerifyResponse.DeficiencyDto pending = DeficiencyDtoHelper.pendingReview(req);
                persistDeficiency(req, pending);
                return new VerifyResponse(UUID.randomUUID(), req.usid(), req.claimType(),
                    VERDICT_PENDING, 0.0, null, trail, pending);
            }
        }

        VerifyResponse.DeficiencyDto failure = DeficiencyDtoHelper.unverified(req);
        persistDeficiency(req, failure);
        return new VerifyResponse(UUID.randomUUID(), req.usid(), req.claimType(),
            VERDICT_FAILED, 0.0, null, trail, failure);
    }

    /**
     * Wallet write that honours the caller key. A key that already produced a claim
     * for this scholar is caught earlier, by the pre-check in {@link #run}; this
     * handles the case where the row appeared between the pre-check and this write.
     */
    private Claim persistClaimIdempotent(VerifyRequest req, String tier, double confidence,
        ZonedDateTime validUntil, String value) {
        String key = normalizedKey(req.idempotencyKey());
        if (key == null) {
            return claims.save(newClaim(req, tier, confidence, validUntil, value));
        }
        try {
            // saveAndFlush, not save: save defers the INSERT (and therefore the
            // unique-index check) to commit, where the exception can no longer be
            // attributed to this attempt.
            return claims.saveAndFlush(newClaim(req, tier, confidence, validUntil, value));
        } catch (DataIntegrityViolationException race) {
            log.info("Verification id={} key={} lost the race for this wallet row; "
                + "rolling back and replaying the winner's claim.", req.usid(), key);
            throw new ClaimKeyRaceException(key, race);
        }
    }

    private Claim newClaim(VerifyRequest req, String tier, double confidence,
        ZonedDateTime validUntil, String value) {
        Claim c = new Claim();
        c.usid = req.usid();
        c.claimType = req.claimType();
        c.source = tier;
        c.method = tier;
        c.confidence = confidence;
        c.verifiedAt = ZonedDateTime.now();
        c.validUntil = validUntil;
        c.evidenceRef = req.evidenceRef();
        c.verifier = "verification-orchestrator";
        c.idempotencyKey = normalizedKey(req.idempotencyKey());
        c.valueEncrypted = cipher.seal(value == null || value.isBlank() ? VALUELESS : value);
        return c;
    }

    /** Marker for a claim whose value the verification endpoint never received. */
    static final String VALUELESS = "";

    /** Replay: same claim, same verdict — no verification ran, so no trail. */
    private VerifyResponse replayResponse(Claim existing) {
        String verdict = "gov_verified".equals(existing.method)
            ? VERDICT_VERIFIED : existing.method;
        return new VerifyResponse(existing.id, existing.usid, existing.claimType, verdict,
            existing.confidence, existing.validUntil, List.of(), null);
    }

    /** Blank and missing keys are the same: no idempotency requested. */
    static String normalizedKey(String raw) {
        if (raw == null || raw.isBlank()) return null;
        return raw.trim();
    }

    private void persistDeficiency(VerifyRequest req, VerifyResponse.DeficiencyDto d) {
        Deficiency entity = new Deficiency();
        entity.id = d.id();
        entity.usid = req.usid();
        entity.type = d.type();
        entity.message = d.message();
        entity.resolutionRoute = d.resolutionRoute();
        entity.status = d.status();
        deficiencies.save(entity);
    }
}
