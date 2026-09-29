package in.adivritti.core.verification.repository;

import in.adivritti.core.verification.entity.Deficiency;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeficiencyRepository extends JpaRepository<Deficiency, UUID> {

    List<Deficiency> findByUsidAndStatusOrderByCreatedAtDesc(UUID usid, String status);

    List<Deficiency> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);
}
