package in.adivritti.core.consent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "consent_artefact")
public class ConsentArtefact {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "purpose", nullable = false, length = 128)
    public String purpose;

    /** Purpose-bound field list. Real JSONB array mapping (see Scholar.demographics). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope", nullable = false, columnDefinition = "jsonb")
    public List<String> scope = new ArrayList<>();

    @Column(name = "granted_by", nullable = false, length = 64)
    public String grantedBy;

    @Column(name = "granted_at", nullable = false)
    public ZonedDateTime grantedAt = ZonedDateTime.now();

    @Column(name = "expires_at")
    public ZonedDateTime expiresAt;

    @Column(name = "revoked_at")
    public ZonedDateTime revokedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (grantedAt == null) grantedAt = ZonedDateTime.now();
        if (scope == null) scope = new ArrayList<>();
    }

    public boolean active() {
        if (revokedAt != null) return false;
        return expiresAt == null || ZonedDateTime.now().isBefore(expiresAt);
    }
}
