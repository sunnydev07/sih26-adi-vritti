package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.adapter.GovAdapter;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tier 1: an authoritative government system confirms the claim outright. */
@Component
public class AuthoritativeStrategy implements VerificationStrategy {

    private final List<GovAdapter> adapters;

    public AuthoritativeStrategy(List<GovAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override
    public String tier() {
        return "gov_verified";
    }

    @Override
    public Optional<TierResult> attempt(VerificationAttempt attempt) {
        return attempt.probe(adapters).stream()
            .filter(GovAdapter.CheckResult::verified)
            .findFirst()
            .map(r -> new TierResult(true, r.confidence(), "government-api", "api", r.note(),
                r.value()));
    }
}
