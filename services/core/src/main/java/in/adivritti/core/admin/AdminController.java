package in.adivritti.core.admin;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.dto.AdminDtos.ExceptionPage;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/admin")
public class AdminController {

    private final CoverageGapService gaps;

    public AdminController(CoverageGapService gaps) {
        this.gaps = gaps;
    }

    @GetMapping("/coverage-gap")
    ResponseEntity<CoverageGapResponse> coverageGap(
        @RequestParam(required = false) String state,
        @RequestParam(required = false) String district,
        @RequestParam(required = false) String block,
        @RequestParam(required = false) String school,
        @RequestParam(required = false) Boolean pvtgFilter) {
        return ResponseEntity.ok(gaps.gap(state, district, block, school, pvtgFilter));
    }

    @GetMapping("/exceptions")
    ResponseEntity<ExceptionPage> exceptions(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(defaultValue = "breach_risk") String sort,
        @RequestParam(required = false) String stage,
        @RequestParam(required = false) String scheme) {
        return ResponseEntity.ok(new ExceptionPage(List.of(), 0, page, pageSize));
    }
}
