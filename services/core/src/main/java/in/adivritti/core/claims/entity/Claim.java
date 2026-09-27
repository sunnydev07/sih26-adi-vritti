package in.adivritti.core.claims.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class Claim {
    @Id
    public UUID id;
    public UUID usid;
    @Column(name = "claim_type")
    public String claimType;
    @Column(name = "value_encrypted")
    public byte[] valueEncrypted;
    public String source;
    public String method;
    public double confidence;
    @Column(name = "verified_at")
    public ZonedDateTime verifiedAt = ZonedDateTime.now();
    @Column(name = "valid_until")
    public ZonedDateTime validUntil;
    @Column(name = "evidence_ref")
    public String evidenceRef;
    public String verifier;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (verifiedAt == null) verifiedAt = ZonedDateTime.now();
    }

    public boolean expired() {
        return validUntil != null && ZonedDateTime.now().isAfter(validUntil);
    }
}
