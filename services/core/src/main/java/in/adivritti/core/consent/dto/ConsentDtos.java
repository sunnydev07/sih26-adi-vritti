package in.adivritti.core.consent.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ConsentCreateRequest(UUID usid, String purpose, List<String> scope,
    String grantedBy, ZonedDateTime expiresAt) {}

public record ConsentDto(UUID id, UUID usid, String purpose, List<String> scope,
    String grantedBy, ZonedDateTime grantedAt, ZonedDateTime expiresAt, ZonedDateTime revokedAt) {}

public record AuditEventDto(long id, String accessor, String fieldAccessed,
    UUID consentId, ZonedDateTime accessedAt) {}

public record AuditPage(List<AuditEventDto> items, long total, int page, int pageSize) {}
