package in.adivritti.core.identity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scholar")
public class Scholar {

    @Id
    @Column(name = "usid", nullable = false)
    public UUID usid;

    /**
     * Demographics snapshot. Mapped as a real JSONB column: declaring the field as
     * String with columnDefinition="jsonb" makes Hibernate bind a VARCHAR and
     * PostgreSQL rejects it, so the JSON type code is required here.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "demographics", nullable = false, columnDefinition = "jsonb")
    public Map<String, Object> demographics = new LinkedHashMap<>();

    /** Guardian USID for minors (Pre-Matric). Kept as a scalar, not a relation, to
     *  avoid self-referential lazy loading on the scholar graph. */
    @Column(name = "guardian_usid")
    public UUID guardianUsid;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (usid == null) usid = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
        if (demographics == null) demographics = new LinkedHashMap<>();
    }
}
