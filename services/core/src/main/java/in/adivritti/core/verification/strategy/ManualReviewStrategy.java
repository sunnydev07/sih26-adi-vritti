package in.adivritti.core.verification.strategy;

import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tier 4: the terminal fallback. It never "verifies" — it routes the claim to the
 * officer worksheet queue and reports {@code verified=false}, which the orchestrator
 * turns into a {@code pending_review} verdict plus a deficiency.
 */
@Component
public class ManualReviewStrategy implements VerificationStrategy {

    @Override
    public String tier() {
        return "pending_review";
    }

    @Override
    public Optional<TierResult> attempt(VerificationAttempt attempt) {
        return Optional.of(new TierResult(false, 0.0, "officer-queue", "manual-review",
            "Routed to officer review with pre-filled worksheet", null));
    }
}
