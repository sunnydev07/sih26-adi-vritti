package in.adivritti.core.application;

import in.adivritti.core.application.dto.ApplicationDtos.ApplicationTimelineResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{id}/timeline")
    ResponseEntity<ApplicationTimelineResponse> timeline(@PathVariable UUID id) {
        return ResponseEntity.ok(service.timeline(id));
    }
}
