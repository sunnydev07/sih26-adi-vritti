package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class PfmsAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public PfmsAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "PFMS"; }
    @Override public boolean supports(String claimType) {
        return Set.of("bank_account", "identity").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/pfms/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.97 : 0.0, "PFMS account validation", null);
    }
}
