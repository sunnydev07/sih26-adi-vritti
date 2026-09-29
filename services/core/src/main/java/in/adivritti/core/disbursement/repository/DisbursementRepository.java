package in.adivritti.core.disbursement.repository;

import in.adivritti.core.disbursement.entity.Disbursement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisbursementRepository extends JpaRepository<Disbursement, UUID> {

    List<Disbursement> findByUsidOrderByScheme(UUID usid);

    /** Money snapshot aggregates for the student dashboard. */
    @Query("""
        select coalesce(sum(d.sanctionedAmountPaise), 0),
               coalesce(sum(d.paidAmountPaise), 0)
        from Disbursement d
        where d.usid = :usid
        """)
    Object[] sumAmountsByUsid(@Param("usid") UUID usid);
}
