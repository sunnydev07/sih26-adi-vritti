package in.adivritti.core.verification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Verification request/response. A failure NEVER blocks — it carries a deficiency. */
public final class VerifyDtos {
    private VerifyDtos() {}

    /**
     * @param claimType one of the wallet claim types defined by the contract
     * @param idempotencyKey optional caller key; repeated submissions for the same
     *                       key resolve to the same claim
     */
    public record VerifyRequest(
        @NotNull(message = "usid is required") UUID usid,
        @NotBlank(message = "claimType is required")
        @Pattern(regexp = "^[a-z_]{2,32}$",
            message = "claimType must be a lowercase snake_case claim name")
        String claimType,
        @Size(max = 512, message = "evidenceRef must be at most 512 characters")
        String evidenceRef,
        @Size(max = 128, message = "idempotencyKey must be at most 128 characters")
        String idempotencyKey) {}

    public record VerifyResponse(UUID claimId, UUID usid, String claimType, String verdict,
        double confidence, ZonedDateTime validUntil, List<ProvenanceRecord> provenance,
        DeficiencyDto deficiency) {
        public record ProvenanceRecord(String tier, String method, String source,
            ZonedDateTime attemptedAt, double confidence) {}
        public record DeficiencyDto(UUID id, String type, String message, String resolutionRoute,
            String status) {}
    }
}
