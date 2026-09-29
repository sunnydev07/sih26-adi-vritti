package in.adivritti.core.claims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "claim")
public class Claim {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "claim_type", nullable = false, length = 32)
    public String claimType;

    /** AES-256-GCM sealed value (see ClaimValueCipher). Never plaintext. */
    @Column(name = "value_encrypted", nullable = false)
    public byte[] valueEncrypted;

    @Column(name = "source", nullable = false, length = 64)
    public String source;

    @Column(name = "method", nullable = false, length = 32)
    public String method;

    @Column(name = "confidence", nullable = false)
    public double confidence;

    @Column(name = "verified_at", nullable = false)
    public ZonedDateTime verifiedAt = ZonedDateTime.now();

    @Column(name = "valid_until", nullable = false)
    public ZonedDateTime validUntil;

    @Column(name = "evidence_ref", length = 512)
    public String evidenceRef;

    @Column(name = "verifier", length = 128)
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
