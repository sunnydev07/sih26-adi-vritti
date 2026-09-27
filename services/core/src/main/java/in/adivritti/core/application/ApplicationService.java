package in.adivritti.core.application;

import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse;
import in.adivritti.core.common.exception.NotFoundException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ApplicationService {

    private final SlaCalculator sla;

    public ApplicationService(SlaCalculator sla) {
        this.sla = sla;
    }

    public ApplicationTimelineResponse timeline(UUID id) {
        if (id == null) throw new NotFoundException("APPLICATION_NOT_FOUND", "Application not found");
        // Repository-backed in full wiring; shape matches the contract.
        ZonedDateTime created = ZonedDateTime.now().minusDays(9);
        return new ApplicationTimelineResponse(id, "district_nodal", "district_nodal",
            created.plusDays(sla.slaDays("district_nodal")), sla.daysElapsed(created),
            List.of(new ApplicationTimelineResponse.TimelineEvent(
                "submitted", "student", created, "Application submitted")));
    }
}
