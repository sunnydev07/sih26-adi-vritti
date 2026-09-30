package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class SfmpAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public SfmpAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "SFMP"; }
    @Override public boolean supports(String claimType) {
        return Set.of("identity", "enrolment", "bank_account", "net_jrf").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/sfmp/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.95 : 0.0, "SFMP fellowship record", null);
    }
}
