package in.adivritti.core.application.repository;

import in.adivritti.core.application.entity.Application;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByUsidOrderByCreatedAtDesc(UUID usid);

    long countByUsidAndSchemeAndAcademicYear(UUID usid, String scheme, String academicYear);

    /** Exception-queue filters. Spring Data derives these from the method name. */
    Page<Application> findByStage(String stage, Pageable pageable);

    Page<Application> findByScheme(String scheme, Pageable pageable);

    Page<Application> findByStageAndScheme(String stage, String scheme, Pageable pageable);

    /**
     * Unpaged variants for the {@code breach_risk} sort: risk is computed in
     * Java, not stored, so the queue loads the filtered set and orders it in
     * memory. Fine at console scale; revisit with a stored column if the
     * exception queue ever pages over large filtered sets.
     */
    List<Application> findByStage(String stage);

    List<Application> findByScheme(String scheme);

    List<Application> findByStageAndScheme(String stage, String scheme);
}
