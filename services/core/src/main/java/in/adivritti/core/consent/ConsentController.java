package in.adivritti.core.consent;

import in.adivritti.core.consent.dto.ConsentDtos.AuditPage;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentCreateRequest;
import in.adivritti.core.consent.dto.ConsentDtos.ConsentDto;
import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
@Validated
public class ConsentController {

    private final ConsentService service;
    private final ScholarAccessGuard access;

    public ConsentController(ConsentService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    @PostMapping("/v1/consent")
    ResponseEntity<ConsentDto> grant(@Valid @RequestBody ConsentCreateRequest req) {
        access.check(req.usid());
        return ResponseEntity.status(HttpStatus.CREATED).body(service.grant(req));
    }

    @DeleteMapping("/v1/consent/{id}")
    ResponseEntity<ConsentDto> revoke(@PathVariable UUID id) {
        // Without this any authenticated caller could revoke any consent by UUID, which
        // is both a denial-of-service on the scholar and a DPDP breach of their control
        // over their own data.
        access.check(service.usidOf(id));
        return ResponseEntity.ok(service.revoke(id));
    }

    /** The access audit is the DPDP "who looked at my data" record — owner or officer. */
    @GetMapping("/v1/scholars/{usid}/audit")
    ResponseEntity<AuditPage> audit(@PathVariable UUID usid,
        @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        access.check(usid);
        // Service pages are 0-indexed; the contract is 1-indexed. Echo the
        // request's page like the exception queue does: returning the 0-based
        // number made clients feed page 0 back in (400) or re-read page 1 forever.
        AuditPage result = service.audit(usid, page - 1, pageSize);
        return ResponseEntity.ok(
            new AuditPage(result.items(), result.total(), page, result.pageSize()));
    }
}
