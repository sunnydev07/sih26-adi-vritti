package in.adivritti.core.identity.repository;

import in.adivritti.core.identity.entity.IdentityResolution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityResolutionRepository extends JpaRepository<IdentityResolution, UUID> {
    Optional<IdentityResolution> findByIdempotencyKey(String idempotencyKey);
}
