package in.adivritti.core.application;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** SLA deadlines per stage + delay-risk scoring from stage timestamps. */
@Component
public class SlaCalculator {

    public static final List<String> STAGES = List.of("submitted", "institute_verification",
        "district_nodal", "state_dept", "ministry", "pfms_payment", "disbursed");

    private static final Map<String, Integer> SLA_DAYS = Map.of(
        "submitted", 2, "institute_verification", 7, "district_nodal", 10,
        "state_dept", 10, "ministry", 15, "pfms_payment", 7, "disbursed", 0);

    public int slaDays(String stage) {
        return SLA_DAYS.getOrDefault(stage, 7);
    }

    public long daysElapsed(ZonedDateTime since) {
        return ChronoUnit.DAYS.between(since, ZonedDateTime.now());
    }

    /** 0..1 breach risk: elapsed / SLA clamped, boosted when already past deadline. */
    public double breachRisk(String stage, ZonedDateTime stageEnteredAt) {
        int sla = Math.max(slaDays(stage), 1);
        double ratio = (double) daysElapsed(stageEnteredAt) / sla;
        return Math.min(1.0, Math.round(ratio * 100.0) / 100.0);
    }
}
