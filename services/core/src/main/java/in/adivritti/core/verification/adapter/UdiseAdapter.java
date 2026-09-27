package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UdiseAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public UdiseAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "UDISE+"; }
    @Override public boolean supports(String claimType) {
        return Set.of("enrolment", "institution", "identity", "domicile").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/udise/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.9 : 0.0, "UDISE+ enrolment record");
    }
}
