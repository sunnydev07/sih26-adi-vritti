package in.adivritti.core.disbursement.entity;

import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "disbursement")
public class Disbursement {
    @Id
    public UUID id;
    public UUID usid;
    public String scheme;
    @Column(name = "sanctioned_amount_paise")
    public long sanctionedAmountPaise;
    @Column(name = "paid_amount_paise")
    public long paidAmountPaise;
    @Column(name = "pfms_ref")
    public String pfmsRef;
    @Column(name = "failure_code")
    public String failureCode;
    @Column(name = "failure_reason")
    public String failureReason;
    public String status = "pending";
    @Column(name = "disbursed_at")
    public ZonedDateTime disbursedAt;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }
}
