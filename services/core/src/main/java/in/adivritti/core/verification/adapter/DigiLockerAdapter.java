package in.adivritti.core.verification.adapter;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import java.util.Map;
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

    /**
     * Wallet claim type to the eligibility rule key the issued document answers.
     * Claim types with no rule key ({@code domicile}, {@code identity}) verify
     * without a value — the wallet entry stays valueless rather than guessing.
     */
    private static final Map<String, String> VALUE_FIELDS = Map.of(
        "income", "family_income_annual_paise",
        "st_status", "st_or_pvtg_status",
        "academic", "class_level");

    @Override public CheckResult check(VerifyRequest req) {
        // OAuth per-fetch consent; govsim proxy stands in when the sandbox is down.
        var res = govsim.get("/digilocker/verify?usid=" + req.usid() + "&claim=" + req.claimType());
        boolean ok = Boolean.TRUE.equals(res.get("verified"));
        String note = ok ? "DigiLocker issued document"
            : String.valueOf(res.getOrDefault("reason_code", "DigiLocker rejected document"));
        return new CheckResult(ok, ok ? 0.99 : 0.0, note, ok ? fieldValue(res, req.claimType()) : null);
    }

    /** Pull the document field answering this claim type out of the govsim payload. */
    @SuppressWarnings("unchecked")
    static String fieldValue(Map<String, Object> res, String claimType) {
        String field = VALUE_FIELDS.get(claimType);
        if (field == null || !(res.get("fields") instanceof Map<?, ?> fields)) return null;
        Object raw = fields.get(field);
        if (raw == null) return null;
        String text = String.valueOf(raw).trim();
        return text.isEmpty() ? null : text;
    }
}
