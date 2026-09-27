package in.adivritti.core.identity.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "scholar")
public class Scholar {
    @Id
    public UUID usid;
    @Column(columnDefinition = "jsonb")
    public String demographics = "{}";
    @Column(name = "guardian_usid")
    public UUID guardianUsid;
    @Column(name = "created_at")
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (usid == null) usid = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
