package in.adivritti.core.application.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "application")
public class Application {
    @Id
    public UUID id;
    public UUID usid;
    public String scheme;
    @Column(name = "academic_year")
    public String academicYear;
    public String stage = "submitted";
    @Column(name = "current_actor")
    public String currentActor = "institute";
    @Column(name = "sla_deadline")
    public ZonedDateTime slaDeadline;
    @Column(name = "created_at")
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
