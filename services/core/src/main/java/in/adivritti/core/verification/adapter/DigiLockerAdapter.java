package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DigiLockerAdapter implements GovAdapter {
    private final GovsimClient govsim;
    public DigiLockerAdapter(GovsimClient govsim) { this.govsim = govsim; }
    @Override public String name() { return "DigiLocker"; }
    @Override public boolean supports(String claimType) {
        // THE live integration: caste/community, income, domicile, marksheets via API Setu.
        return Set.of("st_status", "income", "domicile", "academic", "identity").contains(claimType);
    }
    @Override public CheckResult check(VerifyRequest req) {
        // OAuth per-fetch consent; govsim proxy stands in when the sandbox is down.
        var res = govsim.get("/digilocker/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        return new CheckResult(ok, ok ? 0.99 : 0.0, "DigiLocker issued document");
    }
}
