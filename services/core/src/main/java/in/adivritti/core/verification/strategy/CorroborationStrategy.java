package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.adapter.GovAdapter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Tier 2: two independent systems agree.
 *
 * <p>Probes through the shared attempt memo, so this tier never re-issues the HTTP
 * calls the authoritative tier already made.
 */
@Component
public class CorroborationStrategy implements VerificationStrategy {

    private static final long REQUIRED_AGREEMENTS = 2;
    private static final double CONFIDENCE = 0.85;

    private final List<GovAdapter> adapters;

    public CorroborationStrategy(List<GovAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override
    public String tier() {
        return "corroborated";
    }

    @Override
    public Optional<TierResult> attempt(VerificationAttempt attempt) {
        long agreeing = attempt.agreeing(adapters);
        if (agreeing >= REQUIRED_AGREEMENTS) {
            return Optional.of(new TierResult(true, CONFIDENCE, "cross-system", "corroboration",
                agreeing + " independent systems agree"));
        }
        return Optional.empty();
    }
}
