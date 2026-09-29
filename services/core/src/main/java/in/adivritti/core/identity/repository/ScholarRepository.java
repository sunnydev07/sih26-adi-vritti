package in.adivritti.core.identity.repository;

import in.adivritti.core.identity.entity.Scholar;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ScholarRepository extends JpaRepository<Scholar, UUID> {

    /**
     * Look up a scholar by their vaulted Aadhaar reference key, which lives inside the
     * demographics JSONB document. Done in SQL so duplicate detection does not scan
     * and materialise the whole scholar table.
     */
    @Query("""
        select s from Scholar s
        where s.demographics['aadhaarRefKey'] = :refKey
        """)
    List<Scholar> findByAadhaarRefKey(@Param("refKey") String refKey);
}
