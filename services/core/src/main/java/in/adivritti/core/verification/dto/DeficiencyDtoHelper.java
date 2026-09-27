package in.adivritti.core.verification.dto;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse.DeficiencyDto;
import java.util.UUID;

/** Every mismatch produces an actionable deficiency with a resolution route. */
public final class DeficiencyDtoHelper {
    private DeficiencyDtoHelper() {}

    public static DeficiencyDto pendingReview(VerifyRequest req) {
        return new DeficiencyDto(UUID.randomUUID(), "manual_review",
            "Your " + req.claimType() + " document needs an officer to look at it.",
            "No action needed — track it under 'Where each file is stuck'.", "open");
    }

    public static DeficiencyDto unverified(VerifyRequest req) {
        return new DeficiencyDto(UUID.randomUUID(), "verification_failed",
            "We could not verify " + req.claimType() + " automatically.",
            "Re-upload a clearer document or fetch it from DigiLocker.", "open");
    }
}
