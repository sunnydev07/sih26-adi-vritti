package in.adivritti.core.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "scholar_system_link",
    uniqueConstraints = @UniqueConstraint(name = "uq_syslink_system_external",
        columnNames = {"system_name", "external_id"}))
public class ScholarSystemLink {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "system_name", nullable = false, length = 16)
    public String systemName;

    @Column(name = "external_id", nullable = false, length = 64)
    public String externalId;

    @Column(name = "match_confidence", nullable = false)
    public double matchConfidence = 1.0;

    @Column(name = "resolution_method", nullable = false, length = 32)
    public String resolutionMethod = "deterministic";

    @Column(name = "linked_at", nullable = false)
    public ZonedDateTime linkedAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (linkedAt == null) linkedAt = ZonedDateTime.now();
    }
}
