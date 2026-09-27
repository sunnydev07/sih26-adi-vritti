package in.adivritti.core.claims.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ClaimsListResponse(UUID usid, List<ClaimDto> claims) {
    public record ClaimDto(UUID id, UUID usid, String claimType, String source, String method,
        double confidence, ZonedDateTime verifiedAt, ZonedDateTime validUntil, boolean expired) {}
}
