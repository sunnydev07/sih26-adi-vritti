package in.adivritti.core.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "scheme", nullable = false, length = 16)
    public String scheme;

    @Column(name = "academic_year", nullable = false, length = 8)
    public String academicYear;

    @Column(name = "stage", nullable = false, length = 32)
    public String stage = "submitted";

    @Column(name = "current_actor", nullable = false, length = 64)
    public String currentActor = "institute";

    @Column(name = "sla_deadline")
    public ZonedDateTime slaDeadline;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
