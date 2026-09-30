package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NosAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public NosAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "NOS"; }
    @Override public boolean supports(String claimType) {
        return Set.of("identity", "academic").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/nos/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.95 : 0.0, "NOS selection record", null);
    }
}
