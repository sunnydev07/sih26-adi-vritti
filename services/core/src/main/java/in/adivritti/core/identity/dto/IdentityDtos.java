package in.adivritti.core.identity.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** USID identity resolution across NSP / SFMP / NOS / UDISE / APAAR. */
public final class IdentityDtos {
    private IdentityDtos() {}

    /**
     * @param idempotencyKey optional caller-supplied key; a repeat of the same
     *                       resolution returns the original USID instead of minting
     *                       a second one
     */
    public record IdentityResolveRequest(
        @NotEmpty(message = "At least one identity record is required")
        @Size(max = 50, message = "At most 50 records may be resolved at once")
        List<@Valid @NotNull IdentityRecord> records,
        @Size(max = 128) String idempotencyKey) {
        public record IdentityRecord(
            @NotBlank(message = "systemName is required")
            @Size(max = 16) String systemName,
            @NotBlank(message = "externalId is required")
            @Size(max = 64) String externalId,
            @Size(max = 128) String fullName,
            @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$",
                message = "dob must be an ISO date (yyyy-MM-dd)")
            String dob,
            @Pattern(regexp = "^$|male|female|other|other$", message = "unsupported gender")
            String gender,
            @Size(max = 128) String guardianName,
            @Size(max = 32) String institutionCode,
            @Size(max = 64) String district,
            @Pattern(regexp = "^$|^\\d{4}$", message = "bankAccountLast4 must be 4 digits")
            String bankAccountLast4,
            @Size(max = 32) String otrId,
            @Size(max = 128) String aadhaarRefKey,
            /**
             * Raw Aadhaar, accepted only so the vault can derive a reference key.
             * It is converted to a ref key at the boundary and is never persisted,
             * logged, or echoed back in the response.
             */
            @Size(max = 20) String aadhaarNumber) {

            public IdentityRecord {
                if (bankAccountLast4 != null && !bankAccountLast4.matches("[0-9]{4}")) {
                    throw new IllegalArgumentException("bankAccountLast4 must be 4 digits");
                }
            }

            /** Backwards-compatible factory for callers that only hold a ref key. */
            public static IdentityRecord of(String systemName, String externalId, String fullName,
                String dob, String gender, String guardianName, String institutionCode,
                String district, String bankAccountLast4, String otrId, String aadhaarRefKey) {
                return new IdentityRecord(systemName, externalId, fullName, dob, gender,
                    guardianName, institutionCode, district, bankAccountLast4, otrId,
                    aadhaarRefKey, null);
            }
        }
    }

    public record IdentityResolveResponse(
        UUID usid, List<LinkedSystemRecord> linkedRecords,
        double overallConfidence, boolean needsHumanReview, DuplicateFlag duplicateFlag) {
        public record LinkedSystemRecord(
            String systemName, String externalId, double confidence, String resolutionMethod) {}
        public record DuplicateFlag(boolean isDuplicate, List<UUID> duplicateUsids) {}
    }
}
