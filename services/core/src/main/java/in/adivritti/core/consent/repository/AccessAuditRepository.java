package in.adivritti.core.consent.repository;

import in.adivritti.core.consent.entity.AccessAudit;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessAuditRepository extends JpaRepository<AccessAudit, Long> {

    Page<AccessAudit> findByUsidOrderByAccessedAtDesc(UUID usid, Pageable pageable);
}
