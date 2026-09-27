package in.adivritti.core.disbursement;

import in.adivritti.core.disbursement.dto.DisbursementDtos.DisbursementListResponse;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/disbursements")
public class DisbursementController {

    private final DisbursementService service;

    public DisbursementController(DisbursementService service) {
        this.service = service;
    }

    @GetMapping("/{usid}")
    ResponseEntity<DisbursementListResponse> list(@PathVariable UUID usid) {
        return ResponseEntity.ok(service.list(usid));
    }
}
