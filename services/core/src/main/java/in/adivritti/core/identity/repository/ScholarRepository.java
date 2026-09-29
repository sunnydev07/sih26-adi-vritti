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
     *
     * <p>Written as {@code json_value(...)} rather than {@code s.demographics['aadhaarRefKey']}.
     * A JSON-mapped attribute is not a plain Map to the HQL path resolver: the
     * subscript form fails semantic validation at startup with an opaque
     * {@code visitIndexedPathAccessFragment} error, before the first request. json_value
     * also returns text, so the comparison against a String parameter is a text
     * comparison on PostgreSQL rather than jsonb = varchar, which would never match.
     */
    @Query("""
        select s from Scholar s
        where json_value(s.demographics, '$.aadhaarRefKey') = :refKey
        """)
    List<Scholar> findByAadhaarRefKey(@Param("refKey") String refKey);
}
