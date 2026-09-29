package in.adivritti.core.eligibility.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;

/** Eligibility verdicts computed from versioned rules-as-data, never hard-coded. */
public final class EligibilityDtos {
    private EligibilityDtos() {}

    public record EligibilityRequest(
        @NotNull(message = "usid is required") UUID usid,
        @Pattern(regexp = "^[0-9]{4}-[0-9]{2}$",
            message = "academicYear must look like 2026-27")
        String academicYear) {}

    public record EligibilityResponse(UUID usid, String academicYear, List<SchemeVerdict> verdicts) {
        public record SchemeVerdict(String scheme, String verdict, List<String> reasons,
            List<String> missingClaims) {}
    }
}
