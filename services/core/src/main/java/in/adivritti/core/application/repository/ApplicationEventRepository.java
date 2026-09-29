package in.adivritti.core.application.repository;

import in.adivritti.core.application.entity.ApplicationEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, Long> {

    List<ApplicationEvent> findByApplicationIdOrderByTimestampAsc(UUID applicationId);
}
