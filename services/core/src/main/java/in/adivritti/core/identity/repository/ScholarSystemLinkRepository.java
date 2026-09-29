package in.adivritti.core.identity.repository;

import in.adivritti.core.identity.entity.ScholarSystemLink;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScholarSystemLinkRepository extends JpaRepository<ScholarSystemLink, UUID> {

    List<ScholarSystemLink> findByUsid(UUID usid);

    Optional<ScholarSystemLink> findBySystemNameAndExternalId(String systemName, String externalId);

    /** Identity resolution is idempotent: re-resolving the same external records
     *  must return the existing USID rather than mint a second one. */
    Optional<ScholarSystemLink> findFirstBySystemNameAndExternalIdOrderByLinkedAtDesc(
        String systemName, String externalId);
}
