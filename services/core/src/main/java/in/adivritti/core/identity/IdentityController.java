package in.adivritti.core.identity;

import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveRequest;
import in.adivritti.core.identity.dto.IdentityDtos.IdentityResolveResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/identity")
public class IdentityController {

    private final IdentityService service;

    public IdentityController(IdentityService service) {
        this.service = service;
    }

    @PostMapping("/resolve")
    ResponseEntity<IdentityResolveResponse> resolve(@RequestBody IdentityResolveRequest req) {
        return ResponseEntity.ok(service.resolve(req));
    }
}
