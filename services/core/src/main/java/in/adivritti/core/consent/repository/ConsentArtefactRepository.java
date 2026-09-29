package in.adivritti.core.consent.repository;

import in.adivritti.core.consent.entity.ConsentArtefact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ConsentArtefactRepository extends JpaRepository<ConsentArtefact, UUID> {

    List<ConsentArtefact> findByUsidOrderByGrantedAtDesc(UUID usid);

    /** Is there a live, unrevoked consent for this USID + purpose right now? */
    @Query("""
        select c from ConsentArtefact c
        where c.usid = :usid
          and c.purpose = :purpose
          and c.revokedAt is null
          and (c.expiresAt is null or c.expiresAt > CURRENT_TIMESTAMP)
        order by c.grantedAt desc
        """)
    List<ConsentArtefact> findActiveByUsidAndPurpose(@Param("usid") UUID usid,
        @Param("purpose") String purpose);

    Optional<ConsentArtefact> findFirstByUsidAndPurposeOrderByGrantedAtDesc(UUID usid, String purpose);
}
