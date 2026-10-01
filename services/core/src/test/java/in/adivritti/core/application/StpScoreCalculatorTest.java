package in.adivritti.core.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pins the STP triage heuristic: late-stage clean applications clear the
 * console's 85 threshold, early/at-risk/deficient ones do not. The formula is
 * deliberately transparent — any change here is a product decision, so the
 * numbers are asserted exactly.
 */
class StpScoreCalculatorTest {

    private final StpScoreCalculator scorer = new StpScoreCalculator(new SlaCalculator());
    private final ZonedDateTime now = ZonedDateTime.now();

    @Test
    @DisplayName("late-stage clean application clears the auto-approve threshold")
    void ministryCleanClearsThreshold() {
        assertThat(scorer.score("ministry", now, false)).isEqualTo(90.0);
    }

    @Test
    @DisplayName("fresh submission sits well below the threshold")
    void submittedFreshIsMidRange() {
        assertThat(scorer.score("submitted", now, false)).isEqualTo(55.0);
    }

    @Test
    @DisplayName("open deficiency costs 25 points")
    void deficiencyPenalty() {
        assertThat(scorer.score("ministry", now, true)).isEqualTo(65.0);
    }

    @Test
    @DisplayName("full SLA breach costs 40 points")
    void riskPenalty() {
        // submitted SLA is 2 days; 30 days elapsed clamps risk at 1.0.
        assertThat(scorer.score("submitted", now.minusDays(30), false)).isEqualTo(15.0);
    }

    @Test
    @DisplayName("score clamps at zero instead of going negative")
    void clampsAtZero() {
        assertThat(scorer.score("submitted", now.minusDays(30), true)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("disbursed terminal stage scores 100")
    void disbursedIsFull() {
        assertThat(scorer.score("disbursed", now, false)).isEqualTo(100.0);
    }

    @Test
    @DisplayName("unknown stage falls back to the submission base, never a terminal claim")
    void unknownStageFallsBack() {
        assertThat(scorer.score("archived", now, false)).isEqualTo(55.0);
    }

    @Test
    @DisplayName("missing entry timestamp assumes no SLA pressure rather than maximum")
    void nullTimestampAssumesNoRisk() {
        assertThat(scorer.score("ministry", null, false)).isEqualTo(90.0);
    }
}
