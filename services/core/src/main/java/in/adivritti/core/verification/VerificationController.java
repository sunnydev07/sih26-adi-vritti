package in.adivritti.core.verification;

import in.adivritti.core.security.ScholarAccessGuard;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/verify")
public class VerificationController {

    private final VerificationOrchestrator orchestrator;
    private final ScholarAccessGuard access;

    public VerificationController(VerificationOrchestrator orchestrator, ScholarAccessGuard access) {
        this.orchestrator = orchestrator;
        this.access = access;
    }

    /**
     * A successful verification writes a wallet claim; a failed one writes a
     * deficiency. Both are personal-data writes against the USID in the body, which
     * nothing tied to the caller — so any authenticated principal could mint claims
     * and deficiencies for any scholar. Same ownership rule as reading a claim.
     */
    @PostMapping
    ResponseEntity<VerifyResponse> verify(@Valid @RequestBody VerifyRequest req) {
        access.check(req.usid());
        return ResponseEntity.ok(orchestrator.verify(req));
    }
}
