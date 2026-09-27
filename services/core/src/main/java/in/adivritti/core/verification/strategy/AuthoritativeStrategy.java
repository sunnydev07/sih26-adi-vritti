package in.adivritti.core.verification.strategy;

import in.adivritti.core.verification.adapter.GovAdapter;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthoritativeStrategy implements VerificationStrategy {
    private final List<GovAdapter> adapters;

    public AuthoritativeStrategy(List<GovAdapter> adapters) {
        this.adapters = adapters;
    }

    @Override public String tier() { return "gov_verified"; }

    @Override
    public Optional<TierResult> attempt(VerifyRequest req) {
        for (GovAdapter a : adapters) {
            if (a.supports(req.claimType())) {
                var r = a.check(req);
                if (r.verified()) return Optional.of(new TierResult(true, r.confidence(), a.name(), "api", r.note()));
            }
        }
        return Optional.empty();
    }
}
