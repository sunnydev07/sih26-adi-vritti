package in.adivritti.core.admin;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse.SchoolGap;
import in.adivritti.core.admin.repository.CoverageCandidateRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Coverage Gap read-model: served from coverage_candidate rows populated by the
 * AI gap engine's privacy-preserving HMAC join. MoTA sees counts + school lists,
 * never another ministry's student DB.
 *
 * <p>Denominators come from the same table, not a second source: the candidate
 * table holds the enrolled cohort per school (every ministry-join row,
 * including reached students), while the gap aggregation counts only the
 * unreached subset. Applicants per school is enrolled minus gap — a student on
 * record who is not in the gap has applied through the joined ministry feed.
 * Previously the table held unreached rows only, so enrolled and applicants
 * were reported as 0 rather than invented.
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
        String s = blankToNull(state);
        String d = blankToNull(district);
        String b = blankToNull(block);
        String sc = blankToNull(school);
        List<Object[]> gaps = candidates.aggregateGapsBySchool(s, d, b, sc, pvtgOnly);
        Map<List<String>, Long> enrolled = new LinkedHashMap<>();
        for (Object[] row : candidates.aggregateEnrolledBySchool(s, d, b, sc, pvtgOnly)) {
            enrolled.put(key(row), ((Number) row[4]).longValue());
        }

        List<SchoolGap> schools = new ArrayList<>(gaps.size());
        long totalEnrolled = 0;
        long totalApplicants = 0;
        long totalGap = 0;
        for (Object[] row : gaps) {
            long gapCount = ((Number) row[4]).longValue();
            int pvtgGapCount = row[5] == null ? 0 : ((Number) row[5]).intValue();
            // A school with no enrolled rows cannot have gap rows (both come
            // from the same table), so the lookup below always hits; default
            // to the gap count rather than zero to fail closed, never negative.
            long enrolledCount = Math.max(enrolled.getOrDefault(key(row), gapCount), gapCount);
            long applicants = enrolledCount - gapCount;
            totalEnrolled += enrolledCount;
            totalApplicants += applicants;
            totalGap += gapCount;
            schools.add(new SchoolGap(
                (String) row[0], (String) row[1], (String) row[2], (String) row[3],
                Math.toIntExact(enrolledCount), Math.toIntExact(applicants),
                Math.toIntExact(gapCount), pvtgGapCount));
        }
        // Schools fully reached have enrolled rows but no gap rows: they
        // contribute to the denominators even though they need no outreach.
        Set<List<String>> gapKeys = new HashSet<>();
        for (Object[] row : gaps) gapKeys.add(key(row));
        for (Map.Entry<List<String>, Long> e : enrolled.entrySet()) {
            if (gapKeys.contains(e.getKey())) continue;
            totalEnrolled += e.getValue();
            totalApplicants += e.getValue();
        }
        return new CoverageGapResponse(totalEnrolled, totalApplicants, totalGap, schools);
    }

    private static List<String> key(Object[] row) {
        // Geo columns are nullable; List.of rejects nulls, so use the
        // null-tolerant list — a null school still joins correctly.
        return Arrays.asList(
            (String) row[0], (String) row[1], (String) row[2], (String) row[3]);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
