package in.adivritti.core.scholars;

import in.adivritti.core.scholars.dto.DashboardDtos.DashboardResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scholars/{usid}")
public class ScholarsController {

    private final DashboardService dashboards;
    private final ScholarAccessGuard access;

    public ScholarsController(DashboardService dashboards, ScholarAccessGuard access) {
        this.dashboards = dashboards;
        this.access = access;
    }

    @GetMapping("/dashboard")
    ResponseEntity<DashboardResponse> dashboard(@PathVariable UUID usid) {
        access.check(usid);
        return ResponseEntity.ok(dashboards.dashboard(usid));
    }
}
