package in.adivritti.core.admin.dto;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

public record CoverageGapResponse(long totalEnrolled, long totalApplicants, long totalGap,
    List<SchoolGap> schools) {
    public record SchoolGap(String school, String block, String district, String state,
        int enrolledStudents, int applicants, int gapCount, int pvtgGapCount) {}
}

public record ExceptionItem(UUID applicationId, UUID usid, String studentName, String scheme,
    String stage, double stpScore, double breachRisk, ZonedDateTime slaDeadline) {}

public record ExceptionPage(List<ExceptionItem> items, long total, int page, int pageSize) {}
