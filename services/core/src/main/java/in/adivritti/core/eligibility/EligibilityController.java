package in.adivritti.core.eligibility;

import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityRequest;
import in.adivritti.core.eligibility.dto.EligibilityDtos.EligibilityResponse;
import in.adivritti.core.consent.ConsentGate;
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
    private final ConsentGate consent;

    public EligibilityController(EligibilityService service, ScholarAccessGuard access,
                                 ConsentGate consent) {
        this.service = service;
        this.access = access;
        this.consent = consent;
    }

    @PostMapping("/evaluate")
    ResponseEntity<EligibilityResponse> evaluate(@Valid @RequestBody EligibilityRequest req) {
        access.check(req.usid());
        // Evaluation decrypts the claims wallet, so it is a personal-data read
        // like any other: no live purpose-bound grant, no verdicts.
        consent.requireConsent(req.usid(), "claim_verification", "eligibility");
        return ResponseEntity.ok(service.evaluate(req));
    }
}
