package in.adivritti.core.identity.dto;

import java.util.List;
import java.util.UUID;

public record IdentityResolveRequest(List<IdentityRecord> records, String idempotencyKey) {
    public record IdentityRecord(
        String systemName, String externalId, String fullName, String dob,
        String gender, String guardianName, String institutionCode, String district,
        String bankAccountLast4, String otrId, String aadhaarRefKey) {}
}

public record IdentityResolveResponse(
    UUID usid, List<LinkedSystemRecord> linkedRecords,
    double overallConfidence, boolean needsHumanReview, DuplicateFlag duplicateFlag) {
    public record LinkedSystemRecord(
        String systemName, String externalId, double confidence, String resolutionMethod) {}
    public record DuplicateFlag(boolean isDuplicate, List<UUID> duplicateUsids) {}
}
