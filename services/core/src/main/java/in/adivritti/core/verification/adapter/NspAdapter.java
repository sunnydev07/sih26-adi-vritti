package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class NspAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public NspAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "NSP"; }
    @Override public boolean supports(String claimType) {
        return Set.of("identity", "enrolment", "institution", "academic", "bank_account").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/nsp/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.98 : 0.0, "NSP lookup", null);
    }
}
