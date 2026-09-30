package in.adivritti.core.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Frozen record of one idempotent identity resolution.
 *
 * <p>When a resolve request carries an {@code idempotencyKey}, the service stores
 * the response it computed under that key. A repeat submission with the same key
 * is answered from this row — same USID, same report — instead of re-running the
 * resolution (which could mint a second scholar for a genuinely new person whose
 * first response was lost on the wire).
 */
@Entity
@Table(name = "identity_resolution",
    uniqueConstraints = @UniqueConstraint(name = "uq_identity_resolution_key",
        columnNames = {"idempotency_key"}))
public class IdentityResolution {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    public String idempotencyKey;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    /** Serialised {@code List<IdentityResolveResponse.LinkedSystemRecord>} (JSON). */
    @Column(name = "linked_records", nullable = false, columnDefinition = "jsonb")
    public String linkedRecords = "[]";

    @Column(name = "overall_confidence", nullable = false)
    public double overallConfidence;

    @Column(name = "needs_human_review", nullable = false)
    public boolean needsHumanReview;

    /** Serialised list of duplicate USID strings (JSON). */
    @Column(name = "duplicate_usids", nullable = false, columnDefinition = "jsonb")
    public String duplicateUsids = "[]";

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
