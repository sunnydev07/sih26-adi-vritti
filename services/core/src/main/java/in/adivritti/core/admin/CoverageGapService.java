package in.adivritti.core.admin;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse.SchoolGap;
import in.adivritti.core.admin.repository.CoverageCandidateRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage Gap read-model: served from coverage_candidate rows populated by the
 * AI gap engine's privacy-preserving HMAC join. MoTA sees counts + school lists,
 * never another ministry's student DB.
 */
@Service
public class CoverageGapService {

    private final CoverageCandidateRepository candidates;

    public CoverageGapService(CoverageCandidateRepository candidates) {
        this.candidates = candidates;
    }

    @Transactional(readOnly = true)
    public CoverageGapResponse gap(String state, String district, String block,
        String school, Boolean pvtgFilter) {
        boolean pvtgOnly = Boolean.TRUE.equals(pvtgFilter);
        List<Object[]> rows = candidates.aggregateGapsBySchool(
            blankToNull(state), blankToNull(district), blankToNull(block),
            blankToNull(school), pvtgOnly);

        List<SchoolGap> schools = new ArrayList<>(rows.size());
        long totalGap = 0;
        for (Object[] row : rows) {
            long gapCount = ((Number) row[4]).longValue();
            int pvtgGapCount = row[5] == null ? 0 : ((Number) row[5]).intValue();
            totalGap += gapCount;
            schools.add(new SchoolGap(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3],
                0, 0, Math.toIntExact(gapCount), pvtgGapCount));
        }
        // `coverage_candidate` holds unreached students only, so the enrolled and
        // applicant denominators are not derivable from it. They are reported as 0
        // rather than invented; wiring the enrolment source is tracked separately.
        return new CoverageGapResponse(0, 0, totalGap, schools);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
