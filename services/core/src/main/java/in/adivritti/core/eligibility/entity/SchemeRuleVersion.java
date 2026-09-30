package in.adivritti.core.eligibility.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Versioned mirror of {@code packages/rules/} (ADR-001).
 *
 * <p>The rule files on disk are the primary source; this table is the
 * startup-validated copy written by {@code RuleBootstrapRunner} and the
 * fallback {@code EligibilityService} reads when a disk file is missing. The
 * row finally gives the V1 table a writer and a reader.
 */
@Entity
@Table(name = "scheme_rule_version",
    uniqueConstraints = @UniqueConstraint(name = "uq_rule_scheme_year",
        columnNames = {"scheme", "academic_year"}))
public class SchemeRuleVersion {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    /** Scheme key exactly as authored in the rule file (e.g. {@code PRE_MATRIC}). */
    @Column(name = "scheme", nullable = false, length = 16)
    public String scheme;

    @Column(name = "academic_year", nullable = false, length = 8)
    public String academicYear;

    /** Full validated rule-file document (JSON). */
    @Column(name = "rules_json", nullable = false, columnDefinition = "jsonb")
    public String rulesJson;

    @Column(name = "created_at", nullable = false)
    public ZonedDateTime createdAt = ZonedDateTime.now();

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = ZonedDateTime.now();
    }
}
