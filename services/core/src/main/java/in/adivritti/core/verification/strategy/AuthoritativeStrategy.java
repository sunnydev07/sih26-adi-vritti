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
        // Best confidence wins, not first-in-list: several adapters can verify
        // the same claim (identity: NSP 0.98, DigiLocker 0.99, PFMS 0.97,
        // UDISE+ 0.90), and findFirst() let Spring's bean ordering decide the
        // persisted confidence — and whether a value was captured at all.
        return attempt.probe(adapters).stream()
            .filter(GovAdapter.CheckResult::verified)
            .max(java.util.Comparator.comparingDouble(GovAdapter.CheckResult::confidence))
            .map(r -> new TierResult(true, r.confidence(), "government-api", "api", r.note(),
                r.value()));
    }
}
