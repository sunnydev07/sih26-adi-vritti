package in.adivritti.core.application;

import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse;
import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse.TimelineEvent;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.entity.ApplicationEvent;
import in.adivritti.core.application.repository.ApplicationEventRepository;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.common.exception.NotFoundException;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ApplicationService {

    private final SlaCalculator sla;
    private final ApplicationRepository applications;
    private final ApplicationEventRepository events;

    public ApplicationService(SlaCalculator sla, ApplicationRepository applications,
        ApplicationEventRepository events) {
        this.sla = sla;
        this.applications = applications;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public ApplicationTimelineResponse timeline(UUID id) {
        if (id == null) throw new NotFoundException("APPLICATION_NOT_FOUND", "Application not found");
        Application app = applications.findById(id)
            .orElseThrow(() -> new NotFoundException("APPLICATION_NOT_FOUND", "Application not found"));

        List<TimelineEvent> trail = events.findByApplicationIdOrderByTimestampAsc(id).stream()
            .map(e -> new TimelineEvent(e.stage, e.actor, e.timestamp, e.notes))
            .toList();

        // SLA is measured from the current stage's entry, i.e. the latest event for
        // that stage, falling back to creation when the stage has no events yet.
        ZonedDateTime stageEnteredAt = trail.stream()
            .filter(e -> e.stage().equals(app.stage))
            .map(TimelineEvent::timestamp)
            .max(ZonedDateTime::compareTo)
            .orElse(app.createdAt);
        ZonedDateTime deadline = app.slaDeadline != null
            ? app.slaDeadline
            : stageEnteredAt.plusDays(sla.slaDays(app.stage));

        return new ApplicationTimelineResponse(app.id, app.stage, app.currentActor,
            deadline, sla.daysElapsed(stageEnteredAt), trail);
    }

    @Transactional(readOnly = true)
    public List<Application> forScholar(UUID usid) {
        return usid == null ? List.of() : applications.findByUsidOrderByCreatedAtDesc(usid);
    }
}
