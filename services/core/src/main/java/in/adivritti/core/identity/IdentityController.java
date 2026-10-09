package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/identity")
public class IdentityController {

    private static final Logger log = LoggerFactory.getLogger(IdentityController.class);

    private final IdentityService service;
    private final ScholarAccessGuard access;

    public IdentityController(IdentityService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    /**
     * Officer-only: this mints and links USIDs, and its duplicate flag reports whether
     * a person is already enrolled. Left open, it was both a write primitive for
     * arbitrary records and a way to probe the register.
     */
    @PostMapping("/resolve")
    ResponseEntity<IdentityResolveResponse> resolve(@Valid @RequestBody IdentityResolveRequest req) {
        access.checkOfficer();
        try {
            return ResponseEntity.ok(service.resolve(req));
        } catch (DataIntegrityViolationException race) {
            // Concurrent first-time resolves for the same person or caller key:
            // the loser's transaction rolled back on the unique index, and the
            // retry replays against the winner's committed scholar, links, or
            // idempotency row instead of answering 500. Every constraint race
            // on this path (idempotency key, system link, Aadhaar ref) converges
            // on retry; a genuine integrity bug fails the same way on the second
            // attempt and surfaces unchanged.
            log.info("Identity resolve lost a write race; retrying once against committed rows");
            return ResponseEntity.ok(service.resolve(req));
        }
    }
}
