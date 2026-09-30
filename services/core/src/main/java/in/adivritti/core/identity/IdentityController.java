package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/identity")
public class IdentityController {

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
        return ResponseEntity.ok(service.resolve(req));
    }
}
