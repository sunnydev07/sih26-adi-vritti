package in.adivritti.core.identity.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "scholar_system_link",
    uniqueConstraints = @UniqueConstraint(columnNames = {"system_name", "external_id"}))
public class ScholarSystemLink {
    @Id
    public UUID id;
    public UUID usid;
    @Column(name = "system_name")
    public String systemName;
    @Column(name = "external_id")
    public String externalId;
    @Column(name = "match_confidence")
    public double matchConfidence = 1.0;
    @Column(name = "resolution_method")
    public String resolutionMethod = "deterministic";
    @Column(name = "linked_at")
    public ZonedDateTime linkedAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (linkedAt == null) linkedAt = ZonedDateTime.now();
    }
}
