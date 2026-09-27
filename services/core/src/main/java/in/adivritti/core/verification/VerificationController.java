package in.adivritti.core.verification;

import in.adivritti.core.verification.dto.VerifyDtos.VerifyRequest;
import in.adivritti.core.verification.dto.VerifyDtos.VerifyResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/verify")
public class VerificationController {

    private final VerificationOrchestrator orchestrator;

    public VerificationController(VerificationOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping
    ResponseEntity<VerifyResponse> verify(@RequestBody VerifyRequest req) {
        return ResponseEntity.ok(orchestrator.verify(req));
    }
}
