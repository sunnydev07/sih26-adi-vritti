package in.adivritti.core.claims.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Verified Claims Wallet read model. */
public final class ClaimDtos {
    private ClaimDtos() {}

    public record ClaimDto(UUID id, UUID usid, String claimType, String source, String method,
        double confidence, ZonedDateTime verifiedAt, ZonedDateTime validUntil, boolean expired) {}

    public record ClaimsListResponse(UUID usid, List<ClaimDto> claims) {}
}
