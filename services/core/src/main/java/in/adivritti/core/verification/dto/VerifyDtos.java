package in.adivritti.core.verification.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record VerifyRequest(UUID usid, String claimType, String evidenceRef, String idempotencyKey) {}

public record VerifyResponse(UUID claimId, UUID usid, String claimType, String verdict,
    double confidence, ZonedDateTime validUntil, List<ProvenanceRecord> provenance, DeficiencyDto deficiency) {
    public record ProvenanceRecord(String tier, String method, String source,
        ZonedDateTime attemptedAt, double confidence) {}
    public record DeficiencyDto(UUID id, String type, String message, String resolutionRoute, String status) {}
}
