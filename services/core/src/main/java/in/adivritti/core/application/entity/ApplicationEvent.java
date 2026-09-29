package in.adivritti.core.application.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

/** Append-only: the database rejects UPDATE/DELETE/TRUNCATE via trigger. */
@Entity
@Table(name = "application_event")
public class ApplicationEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    public Long id;

    @Column(name = "application_id", nullable = false)
    public UUID applicationId;

    @Column(name = "stage", nullable = false, length = 32)
    public String stage;

    @Column(name = "actor", nullable = false, length = 64)
    public String actor;

    @Column(name = "timestamp", nullable = false)
    public ZonedDateTime timestamp = ZonedDateTime.now();

    @Column(name = "notes")
    public String notes;

    @PrePersist
    void prePersist() {
        if (timestamp == null) timestamp = ZonedDateTime.now();
    }
}
