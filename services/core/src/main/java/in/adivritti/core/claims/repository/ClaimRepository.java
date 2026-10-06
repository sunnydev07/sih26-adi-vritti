package in.adivritti.core.claims.repository;

import in.adivritti.core.claims.entity.Claim;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findByUsidOrderByVerifiedAtDesc(UUID usid);

    /** Wallet default view: live claims only, newest first. */
    @Query("""
        select c from Claim c
        where c.usid = :usid and c.validUntil > CURRENT_TIMESTAMP
        order by c.verifiedAt desc
        """)
    List<Claim> findLiveByUsid(@Param("usid") UUID usid);

    /**
     * Claim snapshot keyed by claim type, for the rules engine. Only live claims are
     * considered — an expired claim must not silently satisfy an eligibility rule.
     */
    @Query("""
        select c.claimType, c from Claim c
        where c.usid = :usid and c.validUntil > CURRENT_TIMESTAMP
        order by c.verifiedAt desc
        """)
    List<Object[]> findLiveByUsidWithTypes(@Param("usid") UUID usid);

    void deleteByUsid(UUID usid);

    /**
     * Idempotency lookup: the wallet row a previous verification with this caller
     * key already produced for this scholar and claim type, if any.
     *
     * <p>The claim type is part of the scope on purpose. A caller key is only
     * meaningful within one operation: the same key string reused for a
     * different claim type is a different verification, and answering it from
     * another type's claim would hand back the wrong verdict under the wrong
     * type. The unique index {@code uq_claim_idempotency} enforces the same
     * scope at the database level.
     */
    Optional<Claim> findFirstByUsidAndClaimTypeAndIdempotencyKey(UUID usid, String claimType,
        String idempotencyKey);
}
