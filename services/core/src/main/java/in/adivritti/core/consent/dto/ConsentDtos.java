package in.adivritti.core.consent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** DPDP consent artefacts + append-only access audit. */
public final class ConsentDtos {
    private ConsentDtos() {}

    public record ConsentCreateRequest(
        @NotNull(message = "usid is required") UUID usid,
        @NotBlank(message = "purpose is required")
        @Size(max = 128) String purpose,
        @Size(max = 50, message = "scope may contain at most 50 entries")
        List<@NotBlank String> scope,
        @NotBlank(message = "grantedBy is required")
        @Size(max = 64) String grantedBy,
        ZonedDateTime expiresAt) {}

    public record ConsentDto(UUID id, UUID usid, String purpose, List<String> scope,
        String grantedBy, ZonedDateTime grantedAt, ZonedDateTime expiresAt, ZonedDateTime revokedAt) {}

    public record AuditEventDto(long id, String accessor, String fieldAccessed,
        UUID consentId, ZonedDateTime accessedAt) {}

    public record AuditPage(List<AuditEventDto> items, long total, int page, int pageSize) {}
}
