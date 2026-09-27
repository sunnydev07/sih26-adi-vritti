package in.adivritti.core.eligibility.dto;

import java.util.List;
import java.util.UUID;

public record EligibilityRequest(UUID usid, String academicYear) {}

public record EligibilityResponse(UUID usid, String academicYear, List<SchemeVerdict> verdicts) {
    public record SchemeVerdict(String scheme, String verdict, List<String> reasons, List<String> missingClaims) {}
}
