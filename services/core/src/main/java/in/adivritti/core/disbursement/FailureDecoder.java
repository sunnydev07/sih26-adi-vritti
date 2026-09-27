package in.adivritti.core.disbursement;

import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * DBT Failure Doctor: maps PFMS/SFMP/NPCI rejection codes to
 * plain-language cause + concrete fix + nearest place to fix it.
 */
@Component
public class FailureDecoder {

    public record Decoded(String cause, String fix) {}

    private static final Map<String, Decoded> TAXONOMY = Map.of(
        "E001_AADHAAR_NOT_SEEDED", new Decoded("Aadhaar not seeded with bank account",
            "Visit the nearest bank branch with your Aadhaar card and ask for Aadhaar seeding (NPCI mapping)."),
        "E002_ACCOUNT_DORMANT", new Decoded("Bank account dormant/inactive",
            "One deposit or withdrawal reactivates it — visit the branch or use a micro-ATM."),
        "E003_IFSC_CHANGED", new Decoded("IFSC changed after bank merger",
            "Update the IFSC to the new merged-bank code in the application (1-tap update), then re-trigger payment."),
        "E004_NAME_MISMATCH", new Decoded("Name mismatch between Aadhaar and bank account",
            "Correct the differing name token via the correction route, then re-verify the claim."),
        "E005_FUNDS_NOT_RELEASED", new Decoded("Sanction issued but funds not released by ministry",
            "Escalate with the sanction order number at the district nodal office; track the PFMS stage."),
        "E006_OTHER", new Decoded("Other / technical failure",
            "Retry after 48 hours; if it persists raise a grievance with the PFMS reference number."));

    public Decoded decode(String failureCode) {
        if (failureCode == null) return new Decoded(null, null);
        return TAXONOMY.getOrDefault(failureCode,
            new Decoded("Unmapped failure code: " + failureCode, "Contact the district nodal officer."));
    }
}
