package in.adivritti.core.application;

import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/applications")
public class ApplicationController {

    private final ApplicationService service;
    private final ScholarAccessGuard access;

    public ApplicationController(ApplicationService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    /**
     * The caller must own the application or hold an officer role. The route took only
     * an application id, so before this any authenticated caller could enumerate
     * timelines — stage history, acting officer, notes — for every UUID.
     */
    @GetMapping("/{id}/timeline")
    ResponseEntity<ApplicationTimelineResponse> timeline(@PathVariable UUID id) {
        access.check(service.usidOf(id));
        return ResponseEntity.ok(service.timeline(id));
    }
}
