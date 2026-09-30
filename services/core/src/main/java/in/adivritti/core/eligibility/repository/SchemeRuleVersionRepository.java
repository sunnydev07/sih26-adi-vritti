package in.adivritti.core.eligibility.repository;

import in.adivritti.core.eligibility.entity.SchemeRuleVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchemeRuleVersionRepository extends JpaRepository<SchemeRuleVersion, UUID> {
    /**
     * Case-insensitive on the scheme: files author {@code PRE_MATRIC} while the
     * request path uses {@code pre-matric}, and both must resolve to one row.
     */
    Optional<SchemeRuleVersion> findBySchemeIgnoreCaseAndAcademicYear(
        String scheme, String academicYear);
}
