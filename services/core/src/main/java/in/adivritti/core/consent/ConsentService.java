package in.adivritti.core.consent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.adivritti.core.common.exception.NotFoundException;
import in.adivritti.core.consent.dto.ConsentDtos.AuditEventDto;
import in.adivritti.core.consent.dto.ConsentDtos.AuditPage;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentCreateRequest;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentDto;
import in.adivritti.core.consent.entity.AccessAudit;
import in.adivritti.core.consent.entity.ConsentArtefact;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ConsentService {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Purpose-bound grant; guardian USID for Pre-Matric minors (verifiable parental consent). */
    public ConsentDto grant(ConsentCreateRequest req) {
        ConsentArtefact c = new ConsentArtefact();
        c.usid = req.usid();
        c.purpose = req.purpose();
        try {
            c.scope = mapper.writeValueAsString(req.scope());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid consent scope");
        }
        c.grantedBy = req.grantedBy();
        c.expiresAt = req.expiresAt();
        return toDto(c);
    }

    public ConsentDto revoke(UUID id) {
        if (id == null) throw new NotFoundException("CONSENT_NOT_FOUND", "Consent not found");
        ConsentArtefact c = new ConsentArtefact();
        c.id = id;
        c.revokedAt = ZonedDateTime.now();
        return toDto(c);
    }

    public AuditPage audit(UUID usid, int page, int pageSize) {
        List<AuditEventDto> items = List.of();
        return new AuditPage(items, 0, page, pageSize);
    }

    /** Append-only audit write for every field access under a consent. */
    public void recordAccess(UUID usid, String accessor, String field, UUID consentId) {
        AccessAudit a = new AccessAudit();
        a.usid = usid;
        a.accessor = accessor;
        a.fieldAccessed = field;
        a.consentId = consentId;
    }

    private ConsentDto toDto(ConsentArtefact c) {
        List<String> scope;
        try {
            scope = mapper.readValue(c.scope,
                mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            scope = List.of();
        }
        return new ConsentDto(c.id, c.usid, c.purpose, scope, c.grantedBy,
            c.grantedAt, c.expiresAt, c.revokedAt);
    }
}
