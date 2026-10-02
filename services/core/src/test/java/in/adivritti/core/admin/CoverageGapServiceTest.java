package in.adivritti.core.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.adivritti.core.admin.dto.AdminDtos.CoverageGapResponse;
import in.adivritti.core.admin.repository.CoverageCandidateRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The coverage denominators are real. Previously the candidate table held
 * unreached rows only, so enrolled and applicants were hard-coded to 0 while
 * the contract and demo script promised school-level counts. Now the table
 * holds the enrolled cohort (reached + unreached) and the service derives
 * enrolled per school, applicants as enrolled minus gap, and true totals.
 */
class CoverageGapServiceTest {

    private CoverageCandidateRepository candidates;
    private CoverageGapService service;

    private static Object[] row(String school, String block, String district, String state,
        long count, Long pvtg) {
        return new Object[]{school, block, district, state, count, pvtg};
    }

    @BeforeEach
    void setUp() {
        candidates = mock(CoverageCandidateRepository.class);
        service = new CoverageGapService(candidates);
    }

    @Test
    @DisplayName("enrolled and applicants derive from the same table as the gap")
    void denominatorsDeriveFromEnrolledRows() {
        when(candidates.aggregateGapsBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of(
                row("Govt HS Bichhiya", "Block-1", "Mandla", "MP", 41L, 3L)));
        when(candidates.aggregateEnrolledBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of(
                row("Govt HS Bichhiya", "Block-1", "Mandla", "MP", 47L, 4L)));

        CoverageGapResponse response = service.gap(null, null, null, null, null);

        assertThat(response.totalEnrolled()).isEqualTo(47L);
        assertThat(response.totalApplicants()).isEqualTo(6L);
        assertThat(response.totalGap()).isEqualTo(41L);
        assertThat(response.schools()).hasSize(1);
        assertThat(response.schools().get(0).enrolledStudents()).isEqualTo(47);
        assertThat(response.schools().get(0).applicants()).isEqualTo(6);
        assertThat(response.schools().get(0).gapCount()).isEqualTo(41);
        assertThat(response.schools().get(0).pvtgGapCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("fully reached schools feed the totals without needing outreach")
    void fullyReachedSchoolsCountTowardTotals() {
        when(candidates.aggregateGapsBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of(
                row("Govt HS Bichhiya", "Block-1", "Mandla", "MP", 41L, 0L)));
        when(candidates.aggregateEnrolledBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of(
                row("Govt HS Bichhiya", "Block-1", "Mandla", "MP", 47L, 0L),
                row("Ashram School Jagdalpur", "Block-2", "Bastar", "CG", 88L, 5L)));

        CoverageGapResponse response = service.gap(null, null, null, null, false);

        assertThat(response.totalEnrolled()).isEqualTo(135L);
        assertThat(response.totalApplicants()).isEqualTo(94L);
        assertThat(response.totalGap()).isEqualTo(41L);
        assertThat(response.schools()).hasSize(1);
    }

    @Test
    @DisplayName("empty tables report zeros, not nulls")
    void emptyTablesReportZeros() {
        when(candidates.aggregateGapsBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of());
        when(candidates.aggregateEnrolledBySchool(isNull(), isNull(), isNull(), isNull(), eq(false)))
            .thenReturn(List.<Object[]>of());

        CoverageGapResponse response = service.gap(null, "", "  ", null, null);

        assertThat(response.totalEnrolled()).isZero();
        assertThat(response.totalApplicants()).isZero();
        assertThat(response.totalGap()).isZero();
        assertThat(response.schools()).isEmpty();
    }

    @Test
    @DisplayName("pvtg filter and blank normalisation reach both aggregations")
    void filtersReachBothAggregations() {
        when(candidates.aggregateGapsBySchool(eq("MP"), isNull(), isNull(), isNull(), eq(true)))
            .thenReturn(List.<Object[]>of());
        when(candidates.aggregateEnrolledBySchool(eq("MP"), isNull(), isNull(), isNull(), eq(true)))
            .thenReturn(List.<Object[]>of());

        service.gap("MP", "", null, "  ", true);

        verify(candidates).aggregateGapsBySchool(eq("MP"), isNull(), isNull(), isNull(), eq(true));
        verify(candidates).aggregateEnrolledBySchool(eq("MP"), isNull(), isNull(), isNull(), eq(true));
    }
}
