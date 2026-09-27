package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.adapter.GovAdapter;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Tier 2: two independent systems agree -> 'corroborated'. */
@Component
public class CorroborationStrategy implements VerificationStrategy {
    private final List<GovAdapter> adapters;

    public CorroborationStrategy(List<GovAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override public String tier() { return "corroborated"; }

    @Override
    public Optional<TierResult> attempt(VerifyRequest req) {
        long agreeing = adapters.stream()
            .filter(a -> a.supports(req.claimType()))
            .map(a -> a.check(req))
            .filter(GovAdapter.CheckResult::verified)
            .count();
        if (agreeing >= 2) {
            return Optional.of(new TierResult(true, 0.85, "cross-system", "corroboration",
                agreeing + " independent systems agree"));
        }
        return Optional.empty();
    }
}
