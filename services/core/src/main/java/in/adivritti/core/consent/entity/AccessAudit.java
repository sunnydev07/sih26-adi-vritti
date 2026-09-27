package in.adivritti.core.consent.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_audit")
public class AccessAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public UUID usid;
    public String accessor;
    @Column(name = "field_accessed")
    public String fieldAccessed;
    @Column(name = "consent_id")
    public UUID consentId;
    @Column(name = "accessed_at")
    public ZonedDateTime accessedAt = ZonedDateTime.now();
}
