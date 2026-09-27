package in.adivritti.core.verification;

import in.adivritti.core.verification.dto.VerifyDtos.DeficiencyDtoHelper;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse.ProvenanceRecord;
import in.adivritti.core.verification.strategy.VerificationStrategy;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Tiered orchestrator: gov-verified -> corroborated -> assisted -> pending-review.
 * Rules: a failure NEVER blocks the applicant (creates a deficiency instead);
 * every attempt writes an immutable provenance record (returned here, persisted by caller).
 */
@Service
public class VerificationOrchestrator {

    private final List<VerificationStrategy> chain;

    public VerificationOrchestrator(List<VerificationStrategy> chain) {
        this.chain = chain.stream()
            .sorted((a, b) -> Integer.compare(tierOrder(a.tier()), tierOrder(b.tier())))
            .toList();
    }

    private static int tierOrder(String tier) {
        return switch (tier) {
            case "gov_verified" -> 0;
            case "corroborated" -> 1;
            case "assisted" -> 2;
            default -> 3;
        };
    }

    @Cacheable(value = "verification", key = "#req.usid() + ':' + #req.claimType()")
    public VerifyResponse verify(VerifyRequest req) {
        List<ProvenanceRecord> trail = new ArrayList<>();
        for (VerificationStrategy strategy : chain) {
            var attempt = strategy.attempt(req);
            if (attempt.isEmpty()) {
                trail.add(new ProvenanceRecord(strategy.tier(), "skip", "n/a", ZonedDateTime.now(), 0.0));
                continue;
            }
            var r = attempt.get();
            trail.add(new ProvenanceRecord(strategy.tier(), r.method(), r.source(), ZonedDateTime.now(), r.confidence()));
            if (r.verified()) {
                return new VerifyResponse(UUID.randomUUID(), req.usid(), req.claimType(),
                    strategy.tier().equals("gov_verified") ? "verified" : strategy.tier(),
                    r.confidence(), ZonedDateTime.now().plusYears(1), trail, null);
            }
            if (strategy.tier().equals("pending_review")) {
                var def = DeficiencyDtoHelper.pendingReview(req);
                return new VerifyResponse(UUID.randomUUID(), req.usid(), req.claimType(),
                    "pending_review", 0.0, null, trail, def);
            }
        }
        var def = DeficiencyDtoHelper.unverified(req);
        return new VerifyResponse(UUID.randomUUID(), req.usid(), req.claimType(),
            "failed", 0.0, null, trail, def);
    }
}
