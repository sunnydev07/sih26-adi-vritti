package in.adivritti.core.application;

import static org.junit.jupiter.api.Assertions.*;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.Test;

class SlaCalculatorTest {

    private final SlaCalculator sla = new SlaCalculator();

    @Test
    void freshStageHasZeroRisk() {
        assertEquals(0.0, sla.breachRisk("district_nodal", ZonedDateTime.now()));
    }

    @Test
    void breachedStageCapsAtOne() {
        assertEquals(1.0, sla.breachRisk("district_nodal", ZonedDateTime.now().minusDays(60)));
    }
}
