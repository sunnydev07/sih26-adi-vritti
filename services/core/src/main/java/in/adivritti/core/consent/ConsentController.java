package in.adivritti.core.consent;

import in.adivritti.core.consent.dto.ConsentDtos.AuditPage;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentCreateRequest;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentDto;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class ConsentController {

    private final ConsentService service;

    public ConsentController(ConsentService service) {
        this.service = service;
    }

    @PostMapping("/v1/consent")
    ResponseEntity<ConsentDto> grant(@RequestBody ConsentCreateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grant(req));
    }

    @DeleteMapping("/v1/consent/{id}")
    ResponseEntity<ConsentDto> revoke(@PathVariable UUID id) {
        return ResponseEntity.ok(service.revoke(id));
    }

    @GetMapping("/v1/scholars/{usid}/audit")
    ResponseEntity<AuditPage> audit(@PathVariable UUID usid,
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(service.audit(usid, page, pageSize));
    }
}
