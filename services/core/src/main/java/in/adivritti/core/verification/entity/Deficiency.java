package in.adivritti.core.verification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * A verification failure is ALWAYS a deficiency with a resolution route, never a hard
 * block on the application (the SLA/deficiency-loop guarantee).
 */
@Entity
@Table(name = "deficiency")
public class Deficiency {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    /**
     * Nullable: a claim can fail verification before the scholar has any
     * application, so a deficiency is not always application-scoped.
     */
    @Column(name = "application_id")
    public UUID applicationId;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "type", nullable = false, length = 64)
    public String type;

    @Column(name = "message", nullable = false)
    public String message;

    @Column(name = "resolution_route", nullable = false)
    public String resolutionRoute;

    @Column(name = "status", nullable = false, length = 16)
    public String status = "open";

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (status == null) status = "open";
    }
}
