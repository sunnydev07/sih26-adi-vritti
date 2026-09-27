package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class UgcNtaAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public UgcNtaAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "UGC-NTA"; }
    @Override public boolean supports(String claimType) {
        return Set.of("net_jrf", "academic").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        var res = govsim.get("/ugc-nta/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.98 : 0.0, "UGC-NTA result record");
    }
}
