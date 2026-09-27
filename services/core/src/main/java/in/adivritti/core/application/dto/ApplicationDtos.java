package in.adivritti.core.application.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record ApplicationTimelineResponse(UUID applicationId, String currentStage, String currentActor,
    ZonedDateTime slaDeadline, long daysElapsed, List<TimelineEvent> events) {
    public record TimelineEvent(String stage, String actor, ZonedDateTime timestamp, String notes) {}
}
