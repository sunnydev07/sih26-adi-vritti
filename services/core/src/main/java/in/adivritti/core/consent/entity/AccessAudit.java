package in.adivritti.core.consent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Append-only DPDP access audit: the database rejects UPDATE/DELETE/TRUNCATE. */
@Entity
@Table(name = "access_audit")
public class AccessAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    public Long id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "accessor", nullable = false, length = 128)
    public String accessor;

    @Column(name = "field_accessed", nullable = false, length = 128)
    public String fieldAccessed;

    @Column(name = "consent_id")
    public UUID consentId;

    @Column(name = "accessed_at", nullable = false)
    public ZonedDateTime accessedAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (accessedAt == null) accessedAt = ZonedDateTime.now();
    }
}
