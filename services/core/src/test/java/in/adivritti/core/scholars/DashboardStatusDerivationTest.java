package in.adivritti.core.scholars;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * The home screen derives each scheme's contract status from its application
 * stage instead of hard-coding {@code in_progress} for every row.
 */
class DashboardStatusDerivationTest {

    static Stream<Arguments> stages() {
        return Stream.of(
            Arguments.of("submitted", "applied"),
            Arguments.of("institute_verification", "in_verification"),
            Arguments.of("district_nodal", "in_verification"),
            Arguments.of("state_dept", "in_verification"),
            Arguments.of("ministry", "in_verification"),
            Arguments.of("pfms_payment", "approved"),
            Arguments.of("disbursed", "disbursed"),
            // Unknown stages are mid-pipeline work, never a terminal outcome.
            Arguments.of("some_future_stage", "in_verification"),
            Arguments.of(null, "in_verification"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("stages")
    @DisplayName("stage maps to the contract status")
    void stageMapsToStatus(String stage, String expected) {
        assertThat(DashboardService.deriveStatus(stage)).isEqualTo(expected);
    }
}
