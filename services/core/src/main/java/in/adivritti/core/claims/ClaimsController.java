package in.adivritti.core.claims;

import in.adivritti.core.claims.dto.ClaimDtos.ClaimsListResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/scholars/{usid}/claims")
public class ClaimsController {

    private final ClaimsService service;

    public ClaimsController(ClaimsService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<ClaimsListResponse> list(@PathVariable UUID usid,
        @RequestParam(defaultValue = "false") boolean includeExpired) {
        return ResponseEntity.ok(service.list(usid, includeExpired));
    }
}
