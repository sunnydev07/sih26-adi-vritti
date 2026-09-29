package in.adivritti.core.claims;

import in.adivritti.core.claims.dto.ClaimDtos.ClaimsListResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/scholars/{usid}/claims")
public class ClaimsController {

    private final ClaimsService service;
    private final ScholarAccessGuard access;

    public ClaimsController(ClaimsService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    /** Claims contain income and bank identifiers, so the caller must own the USID. */
    @GetMapping
    ResponseEntity<ClaimsListResponse> list(@PathVariable UUID usid,
        @RequestParam(defaultValue = "false") boolean includeExpired) {
        access.check(usid);
        return ResponseEntity.ok(service.list(usid, includeExpired));
    }
}
