package in.adivritti.core.scholars;

import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/scholars/{usid}")
public class ScholarsController {

    private final DashboardService dashboards;

    public ScholarsController(DashboardService dashboards) {
        this.dashboards = dashboards;
    }

    @GetMapping("/dashboard")
    ResponseEntity<DashboardResponse> dashboard(@PathVariable UUID usid) {
        return ResponseEntity.ok(dashboards.dashboard(usid));
    }
}
