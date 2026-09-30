package in.adivritti.core.admin;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.dto.AdminDtos.ExceptionItem;
import in.adivritti.core.admin.dto.AdminDtos.ExceptionPage;
import in.adivritti.core.application.SlaCalculator;
import in.adivritti.core.application.entity.Application;
import in.adivritti.core.application.repository.ApplicationRepository;
import in.adivritti.core.security.ScholarAccessGuard;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Officer console endpoints. These expose cross-ministry aggregates and student
 * names, so they require an OFFICER or ADMIN role — both at the filter chain
 * ({@code /v1/admin/**}) and through {@link ScholarAccessGuard#checkOfficer()}, so the
 * rule survives a future path-mapping change and still holds in the dev profile, where
 * the filter chain steps aside for the token-free demo.
 */
@RestController
@RequestMapping("/v1/admin")
@Validated
public class AdminController {

    private final CoverageGapService gaps;
    private final ApplicationRepository applications;
    private final SlaCalculator sla;
    private final ScholarAccessGuard access;

    public AdminController(CoverageGapService gaps, ApplicationRepository applications,
        SlaCalculator sla, ScholarAccessGuard access) {
        this.gaps = gaps;
        this.applications = applications;
        this.sla = sla;
        this.access = access;
    }

    @GetMapping("/coverage-gap")
    ResponseEntity<CoverageGapResponse> coverageGap(
        @RequestParam(name = "state", required = false) @Size(max = 64) String state,
        @RequestParam(name = "district", required = false) @Size(max = 64) String district,
        @RequestParam(name = "block", required = false) @Size(max = 64) String block,
        @RequestParam(name = "school", required = false) @Size(max = 256) String school,
        @RequestParam(name = "pvtg_filter", required = false) Boolean pvtgFilter) {
        access.checkOfficer();
        return ResponseEntity.ok(gaps.gap(state, district, block, school, pvtgFilter));
    }

    /**
     * SLA breach queue. {@code sort=sla_deadline} orders by soonest deadline first —
     * the work an officer should do next. {@code breach_risk} (the default) and
     * {@code created_at} order newest-first: the per-row risk score is computed in
     * {@link #toItem} but is not a sortable column yet.
     */
    @GetMapping("/exceptions")
    ResponseEntity<ExceptionPage> exceptions(
        @RequestParam(name = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(name = "page_size", defaultValue = "20") @Min(1) @Max(100) int pageSize,
        @RequestParam(name = "sort", defaultValue = "breach_risk")
        @Pattern(regexp = "breach_risk|sla_deadline|created_at", message = "unsupported sort")
        String sort,
        @RequestParam(name = "stage", required = false) @Size(max = 32) String stage,
        @RequestParam(name = "scheme", required = false) @Size(max = 16) String scheme) {

        access.checkOfficer();
        Sort order = "sla_deadline".equals(sort)
            ? Sort.by(Sort.Direction.ASC, "slaDeadline")
            : Sort.by(Sort.Direction.DESC, "createdAt");
        var pageable = PageRequest.of(page - 1, pageSize, order);

        boolean hasStage = stage != null && !stage.isBlank();
        boolean hasScheme = scheme != null && !scheme.isBlank();
        var result = hasStage && hasScheme
            ? applications.findByStageAndScheme(stage, scheme, pageable)
            : hasStage
                ? applications.findByStage(stage, pageable)
                : hasScheme
                    ? applications.findByScheme(scheme, pageable)
                    : applications.findAll(pageable);

        List<ExceptionItem> items = result.getContent().stream()
            .map(this::toItem)
            .toList();
        return ResponseEntity.ok(new ExceptionPage(items, result.getTotalElements(),
            page, pageSize));
    }

    /**
     * studentName is intentionally left null here. Rendering a name in the exception
     * queue is a DPDP personal-data access and must go through a consent-gated,
     * audited lookup rather than being smuggled into an officer's exception list.
     */
    private ExceptionItem toItem(Application a) {
        double risk = a.slaDeadline == null ? 0.0
            : sla.breachRisk(a.stage, a.createdAt);
        return new ExceptionItem(a.id, a.usid, null, a.scheme, a.stage, 0.0, risk,
            a.slaDeadline);
    }
}
