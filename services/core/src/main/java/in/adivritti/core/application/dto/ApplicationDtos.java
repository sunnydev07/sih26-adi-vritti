package in.adivritti.core.application.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

/** Application stage-machine timeline (SLA deadlines + append-only event log). */
public final class ApplicationDtos {
    private ApplicationDtos() {}

    public record ApplicationTimelineResponse(UUID applicationId, String currentStage, String currentActor,
        ZonedDateTime slaDeadline, long daysElapsed, List<TimelineEvent> events) {
        public record TimelineEvent(String stage, String actor, ZonedDateTime timestamp, String notes) {}
    }
}
