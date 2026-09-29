package in.adivritti.core.disbursement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "disbursement")
public class Disbursement {

    @Id
    @Column(name = "id", nullable = false)
    public UUID id;

    @Column(name = "usid", nullable = false)
    public UUID usid;

    @Column(name = "scheme", nullable = false, length = 16)
    public String scheme;

    @Column(name = "sanctioned_amount_paise", nullable = false)
    public long sanctionedAmountPaise;

    @Column(name = "paid_amount_paise", nullable = false)
    public long paidAmountPaise;

    @Column(name = "pfms_ref", length = 64)
    public String pfmsRef;

    @Column(name = "failure_code", length = 64)
    public String failureCode;

    @Column(name = "failure_reason")
    public String failureReason;

    @Column(name = "status", nullable = false, length = 16)
    public String status = "pending";

    @Column(name = "disbursed_at")
    public ZonedDateTime disbursedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
