package in.adivritti.core.admin.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Coverage-gap candidate. Holds ONLY an HMAC-hashed identity key — never raw PII —
 * so the cross-ministry join stays privacy-preserving under DPDP.
 */
@Entity
@Table(name = "coverage_candidate")
public class CoverageCandidate {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "hashed_key", nullable = false, unique = true, length = 128)
    public String hashedKey;

    @Column(name = "state", length = 64)
    public String state;

    @Column(name = "district", length = 64)
    public String district;

    @Column(name = "block", length = 64)
    public String block;

    @Column(name = "school", length = 256)
    public String school;

    @Column(name = "class_level")
    public Integer classLevel;

    @Column(name = "gender", length = 16)
    public String gender;

    @Column(name = "pvtg_status", nullable = false)
    public boolean pvtgStatus;

    @Column(name = "outreach_status", nullable = false, length = 32)
    public String outreachStatus = "unreached";

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (outreachStatus == null) outreachStatus = "unreached";
    }
}
