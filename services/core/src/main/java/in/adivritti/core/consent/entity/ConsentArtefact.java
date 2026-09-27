package in.adivritti.core.consent.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "consent_artefact")
public class ConsentArtefact {
    @Id
    public UUID id;
    public UUID usid;
    public String purpose;
    @Column(columnDefinition = "jsonb")
    public String scope = "[]";
    @Column(name = "granted_by")
    public String grantedBy;
    @Column(name = "granted_at")
    public ZonedDateTime grantedAt = ZonedDateTime.now();
    @Column(name = "expires_at")
    public ZonedDateTime expiresAt;
    @Column(name = "revoked_at")
    public ZonedDateTime revokedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (grantedAt == null) grantedAt = ZonedDateTime.now();
    }

    public boolean active() {
        if (revokedAt != null) return false;
        return expiresAt == null || ZonedDateTime.now().isBefore(expiresAt);
    }
}
