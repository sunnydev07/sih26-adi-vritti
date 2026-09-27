package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tier 4: always succeeds by routing to the officer worksheet queue -> 'pending-review'. */
@Component
public class ManualReviewStrategy implements VerificationStrategy {
    @Override public String tier() { return "pending_review"; }

    @Override
    public Optional<TierResult> attempt(VerifyRequest req) {
        return Optional.of(new TierResult(false, 0.0, "officer-queue", "manual-review",
            "Routed to officer review with pre-filled worksheet"));
    }
}
