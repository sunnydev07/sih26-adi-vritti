package in.adivritti.core.disbursement;

import in.adivritti.core.disbursement.dto.DisbursementDtos.DisbursementListResponse;
import in.adivritti.core.security.ScholarAccessGuard;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/disbursements")
public class DisbursementController {

    private final DisbursementService service;
    private final ScholarAccessGuard access;

    public DisbursementController(DisbursementService service, ScholarAccessGuard access) {
        this.service = service;
        this.access = access;
    }

    @GetMapping("/{usid}")
    ResponseEntity<DisbursementListResponse> list(@PathVariable UUID usid) {
        access.check(usid);
        return ResponseEntity.ok(service.list(usid));
    }
}
