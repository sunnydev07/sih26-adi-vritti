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
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tiered orchestrator: gov-verified -&gt; corroborated -&gt; assisted -&gt; pending-review.
 * Rules: a failure NEVER blocks the applicant (creates a deficiency instead);
 * every attempt writes an immutable provenance record (returned here, persisted by caller).
 */
@Service
public class VerificationOrchestrator {

    private static final String VERDICT_VERIFIED = "verified";
    private static final String VERDICT_FAILED = "failed";
    private static final String VERDICT_PENDING = "pending_review";
    private static final int CLAIM_VALIDITY_DAYS = 365;

    /** Marker for a claim whose value the verification endpoint never received. */
    public static final String VALUELESS = "";

    private final List<VerificationStrategy> chain;
    private final ClaimRepository claims;
    private final DeficiencyRepository deficiencies;
    private final ClaimValueCipher cipher;

    public VerificationOrchestrator(List<VerificationStrategy> chain, ClaimRepository claims,
        DeficiencyRepository deficiencies, ClaimValueCipher cipher) {
        this.chain = chain.stream()
            .sorted((a, b) -> Integer.compare(tierOrder(a.tier()), tierOrder(b.tier())))
            .toList();
        this.claims = claims;
        this.deficiencies = deficiencies;
        this.cipher = cipher;
    }

    private static int tierOrder(String tier) {
        return switch (tier) {
            case "gov_verified" -> 0;
            case "corroborated" -> 1;
            case "assisted" -> 2;
            default -> 3;
        };
    }

    /**
     * Only successful verifications are cached. A {@code failed} or
     * {@code pending_review} outcome is a transient state that the next attempt
     * should be able to improve on, so caching it for an hour would strand the
     * applicant in a stale verdict.
     */
    @Cacheable(
        value = "verification",
        key = "T(String).valueOf(#req.usid()) + ':' + T(String).valueOf(#req.claimType())",
        unless = "#result == null || #result.verdict() == 'failed' || #result.verdict() == 'pending_review'")
    @Transactional
    public VerifyResponse verify(VerifyRequest req) {
        if (req == null) throw new IllegalArgumentException("Verify request is required");
        if (req.usid() == null) throw new IllegalArgumentException("usid is required");
        if (req.claimType() == null || req.claimType().isBlank()) {
            throw new IllegalArgumentException("claimType is required");
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
                Claim claim = persistClaim(req, strategy.tier(), r.confidence(), validUntil);
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
     * Persist the wallet entry for a successful verification.
     *
     * <p>The contract's {@code VerifyRequest} carries no claim <em>value</em> — only
     * the type, evidence reference, and idempotency key — so this stores an empty
     * value. It records that the claim was verified (provenance and validity) without
     * inventing a number. The eligibility engine deliberately skips valueless claims,
     * so an unpopulated wallet entry can never satisfy a rule with a fake value.
     */
    private Claim persistClaim(VerifyRequest req, String tier, double confidence,
        ZonedDateTime validUntil) {
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
        c.valueEncrypted = cipher.seal(VALUELESS);
        return claims.save(c);
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
