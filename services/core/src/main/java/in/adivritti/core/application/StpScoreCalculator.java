package in.adivritti.core.application;

import java.time.ZonedDateTime;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Straight-through-processing readiness score, 0..100, for the officer
 * exception queue.
 *
 * <p>This is an assistive heuristic over pipeline state, not a model and not a
 * decision: it answers "how close is this application to clearing without a
 * human touch" so officers can triage. The batch auto-approve threshold lives
 * in the console (stpScore &gt;= 85); only late-stage, in-SLA,
 * deficiency-free applications reach it.
 *
 * <p>Formula: {@code stageBase - 40 * breachRisk - (openDeficiency ? 25 : 0)},
 * clamped to 0..100. Stage bases rise with pipeline progress because each
 * completed verification stage is one fewer reason to keep a human in the
 * loop. SLA pressure and open deficiencies pull the score down because both
 * need officer attention, which is the opposite of straight-through.
 */
@Component
public class StpScoreCalculator {

    private static final Map<String, Integer> STAGE_BASE = Map.of(
        "submitted", 55, "institute_verification", 65, "district_nodal", 72,
        "state_dept", 80, "ministry", 90, "pfms_payment", 97, "disbursed", 100);

    private static final double RISK_PENALTY = 40.0;
    private static final double DEFICIENCY_PENALTY = 25.0;

    private final SlaCalculator sla;

    public StpScoreCalculator(SlaCalculator sla) {
        this.sla = sla;
    }

    public double score(String stage, ZonedDateTime stageEnteredAt,
        boolean hasOpenDeficiency) {
        int base = STAGE_BASE.getOrDefault(stage, 55);
        double risk = stageEnteredAt == null ? 0.0 : sla.breachRisk(stage, stageEnteredAt);
        double raw = base - RISK_PENALTY * risk - (hasOpenDeficiency ? DEFICIENCY_PENALTY : 0.0);
        return Math.round(Math.min(100.0, Math.max(0.0, raw)) * 10.0) / 10.0;
    }
}
