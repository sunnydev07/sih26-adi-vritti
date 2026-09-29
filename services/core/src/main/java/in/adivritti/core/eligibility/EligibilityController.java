package in.adivritti.core.eligibility;

import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/eligibility")
public class EligibilityController {

    private final EligibilityService service;
    private final ScholarAccessGuard access;

    public EligibilityController(EligibilityService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/evaluate")
    ResponseEntity<EligibilityResponse> evaluate(@Valid @RequestBody EligibilityRequest req) {
        access.check(req.usid());
        return ResponseEntity.ok(service.evaluate(req));
    }
}
